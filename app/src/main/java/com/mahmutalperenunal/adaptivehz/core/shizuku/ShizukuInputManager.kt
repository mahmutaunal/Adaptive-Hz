package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.mahmutalperenunal.adaptivehz.BuildConfig
import com.mahmutalperenunal.adaptivehz.core.input.InteractionSignalProvider
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ShizukuInputManager(
    private val onPrivilegedServiceReady: (() -> Unit)? = null,
    private val onPrivilegedServiceUnavailable: (() -> Unit)? = null,
    private val onTouchDownSignal: (() -> Unit)? = null,
    private val onTouchMoveSignal: (() -> Unit)? = null,
    private val onTouchUpSignal: (() -> Unit)? = null
) : InteractionSignalProvider {

    @Volatile
    private var touchActive = false

    @Volatile
    private var lastTouchDownAt = 0L

    @Volatile
    private var lastTouchMoveAt = 0L

    @Volatile
    private var lastTouchUpAt = 0L

    @Volatile
    private var touchscreenDevicePath: String? = null

    @Volatile
    private var inputMonitoringActive = false

    @Volatile
    private var bindingInProgress = false

    @Volatile
    private var bound = false

    @Volatile
    private var destroyed = false

    @Volatile
    private var permissionListenerRegistered = false

    @Volatile
    private var lastBindAttemptAt = 0L

    @Volatile
    private var lastStatusCheckAt = 0L

    @Volatile
    private var statusCheckScheduled = false

    private val lock = Any()
    private val controlExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AdaptiveHzShizukuControl").apply { isDaemon = true }
    }

    private val userServiceArgs: UserServiceArgs by lazy {
        UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                InputMonitorUserService::class.java.name
            )
        )
            .tag(USER_SERVICE_TAG)
            .daemon(false)
            .processNameSuffix(PROCESS_NAME_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            // The protocol revision also changes whenever AIDL does, preventing same-version
            // development builds from reconnecting to an incompatible remote implementation.
            .version(BuildConfig.VERSION_CODE * 100 + USER_SERVICE_API_REVISION)
    }

    private val inputEventCallback =
        object : IInputEventCallback.Stub() {

            override fun onTouchDown() {
                inputMonitoringActive = true
                touchActive = true
                lastTouchDownAt = System.currentTimeMillis()
                Log.d(TAG, "Raw touch DOWN")
                onTouchDownSignal?.invoke()
            }

            override fun onTouchMove() {
                inputMonitoringActive = true
                lastTouchMoveAt = System.currentTimeMillis()
                onTouchMoveSignal?.invoke()
            }

            override fun onTouchUp() {
                inputMonitoringActive = true
                touchActive = false
                lastTouchUpAt = System.currentTimeMillis()
                Log.d(TAG, "Raw touch UP")
                onTouchUpSignal?.invoke()
            }
        }

    private var service: IInputMonitorService? = null

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != ShizukuAccess.UI_PERMISSION_REQUEST_CODE) {
                return@OnRequestPermissionResultListener
            }
            if (destroyed) return@OnRequestPermissionResultListener

            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "permissionResult granted=$granted")

            if (granted) {
                executeControl(::bindUserService)
            }
        }

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "UserService connected name=$name")

            synchronized(lock) {
                if (destroyed) {
                    Log.w(TAG, "Service connected after destroy, unbinding immediately")
                    executeControl {
                        runCatching { Shizuku.unbindUserService(userServiceArgs, this, true) }
                    }
                    return
                }

                service = IInputMonitorService.Stub.asInterface(binder)
                service?.let(ShizukuRefreshRateBridge::attach)
                bound = true
                bindingInProgress = false
            }

            executeControl {
                if (destroyed) return@executeControl
                ShizukuRefreshRateBridge.inspectSamsungRefreshRateTokenApi()?.let { report ->
                    Log.i(TAG, "Samsung refresh-rate token probe:\n$report")
                }

                detectTouchscreenDeviceAndStartMonitoring()
                onPrivilegedServiceReady?.invoke()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "UserService disconnected name=$name")
            handleServiceUnavailable()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "UserService binding died name=$name")
            handleServiceUnavailable()
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "UserService null binding name=$name")
            handleServiceUnavailable()
        }
    }

    /** Invalidates cached token state and lets the engine apply its normal vendor fallback. */
    private fun handleServiceUnavailable() {
        synchronized(lock) {
            bindingInProgress = false
            bound = false
            ShizukuRefreshRateBridge.detach(service)
            service = null
            touchActive = false
            inputMonitoringActive = false
        }
        onPrivilegedServiceUnavailable?.invoke()
    }

    /**
     * Validates Shizuku availability and permission state before binding the input monitor service.
     *
     * This method may be called frequently from Accessibility events, so it must be cheap and
     * must never start duplicate UserService processes.
     */
    fun checkStatus() {
        if (destroyed) return

        val now = SystemClock.elapsedRealtime()

        synchronized(lock) {
            if (service != null && inputMonitoringActive) return
            if (bindingInProgress || statusCheckScheduled) return

            if (now - lastStatusCheckAt < STATUS_CHECK_THROTTLE_MS) {
                return
            }

            lastStatusCheckAt = now
            statusCheckScheduled = true
        }

        executeControl {
            try {
                checkStatusOffMainThread()
            } finally {
                synchronized(lock) { statusCheckScheduled = false }
            }
        }
    }

    private fun checkStatusOffMainThread() {
        if (destroyed) return

        registerPermissionListenerIfNeeded()

        val binderAlive = runCatching { Shizuku.pingBinder() }
            .getOrDefault(false)

        Log.d(TAG, "binderAlive=$binderAlive")

        if (!binderAlive) {
            Log.w(TAG, "Shizuku is not running")
            return
        }

        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        Log.d(TAG, "permissionGranted=$permissionGranted")

        if (!permissionGranted) {
            Log.d(TAG, "Shizuku permission is optional; waiting for an explicit UI request")
            return
        }

        bindUserService()
    }

    private fun executeControl(block: () -> Unit) {
        runCatching { controlExecutor.execute(block) }
            .onFailure { Log.w(TAG, "Shizuku control task was rejected", it) }
    }

    private fun registerPermissionListenerIfNeeded() {
        if (permissionListenerRegistered) return

        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            permissionListenerRegistered = true
        }.onFailure {
            Log.e(TAG, "Failed to register permission listener", it)
        }
    }

    private fun unregisterPermissionListener() {
        if (!permissionListenerRegistered) return

        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }

        permissionListenerRegistered = false
    }

    private fun bindUserService() {
        val now = SystemClock.elapsedRealtime()

        synchronized(lock) {
            if (destroyed) return
            if (service != null || bound || bindingInProgress) {
                Log.d(TAG, "bind skipped: already bound/binding")
                return
            }

            if (now - lastBindAttemptAt < BIND_RETRY_COOLDOWN_MS) {
                Log.d(TAG, "bind skipped: cooldown")
                return
            }

            lastBindAttemptAt = now
            bindingInProgress = true
        }

        Log.d(TAG, "Binding UserService...")

        runCatching {
            Shizuku.bindUserService(userServiceArgs, connection)
            Log.d(TAG, "bindUserService called")
        }.onFailure {
            synchronized(lock) {
                bindingInProgress = false
                bound = false
                service = null
            }

            Log.e(TAG, "bindUserService failed", it)
        }
    }

    private fun detectTouchscreenDeviceAndStartMonitoring() {
        val remote = service
        if (remote == null) {
            Log.w(TAG, "detect skipped: service is null")
            return
        }

        runCatching {
            val result = remote.listInputDevices()
            val detectedPath = parseTouchscreenDevicePath(result)

            touchscreenDevicePath = detectedPath

            Log.d(TAG, "detectedTouchscreenDevice=$detectedPath")

            if (detectedPath != null) {
                startInputMonitoring(detectedPath)
            } else {
                Log.w(TAG, "No touchscreen input device detected")
            }
        }.onFailure {
            Log.e(TAG, "Failed to detect touchscreen input device", it)
            inputMonitoringActive = false
        }
    }

    private fun startInputMonitoring(devicePath: String) {
        Log.d(TAG, "startInputMonitoring called devicePath=$devicePath")

        val remote = service
        if (remote == null) {
            Log.w(TAG, "startInputMonitoring skipped: service is null")
            return
        }

        runCatching {
            remote.startMonitoring(
                devicePath,
                inputEventCallback
            )

            inputMonitoringActive = true
            Log.d(TAG, "Input monitoring start requested")
        }.onFailure {
            inputMonitoringActive = false
            Log.e(TAG, "Failed to start monitoring", it)
        }
    }

    private fun parseTouchscreenDevicePath(geteventOutput: String): String? {
        val blocks = geteventOutput
            .split(Regex("(?=add device \\d+: /dev/input/event\\d+)"))
            .map { it.trim() }
            .filter { it.startsWith("add device") }

        val directTouchscreen = blocks.firstOrNull { block ->
            block.contains("INPUT_PROP_DIRECT") &&
                    block.contains("BTN_TOUCH") &&
                    block.contains("ABS_MT_POSITION_X") &&
                    block.contains("ABS_MT_POSITION_Y")
        }

        val fallbackTouchscreen = blocks.firstOrNull { block ->
            block.contains("touchscreen", ignoreCase = true) &&
                    block.contains("BTN_TOUCH") &&
                    block.contains("ABS_MT_POSITION_X") &&
                    block.contains("ABS_MT_POSITION_Y")
        }

        val selectedBlock = directTouchscreen ?: fallbackTouchscreen ?: return null

        return Regex("/dev/input/event\\d+")
            .find(selectedBlock)
            ?.value
    }

    fun destroy() {
        Log.d(TAG, "destroy")

        destroyed = true
        executeControl {
            unregisterPermissionListener()

            val remote = synchronized(lock) {
                val current = service
                if (current?.asBinder()?.isBinderAlive == true) {
                    val hyperOsRestored = runCatching {
                        current.releaseHyperOsRefreshRateSetting()
                    }.onFailure {
                        Log.w(TAG, "HyperOS cleanup failed before unbind", it)
                    }.getOrDefault(false)
                    if (!hyperOsRestored) {
                        Log.w(TAG, "HyperOS cleanup was rejected before unbind")
                    }
                    val released = runCatching { current.releaseSamsungRefreshRateTokenLimits() }
                        .onFailure { Log.w(TAG, "Token cleanup failed before unbind", it) }
                        .getOrDefault(false)
                    if (!released) Log.w(TAG, "Token cleanup was rejected before unbind")
                }
                ShizukuRefreshRateBridge.detach(current)
                service = null
                inputMonitoringActive = false
                touchActive = false
                bindingInProgress = false
                statusCheckScheduled = false
                bound = false
                current
            }

            runCatching { remote?.stopMonitoring() }
                .onFailure { Log.e(TAG, "stopMonitoring failed during destroy", it) }

            runCatching {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
                Log.d(TAG, "unbindUserService called")
            }.onFailure {
                Log.e(TAG, "unbindUserService failed", it)
            }
        }
    }

    override fun isTouchActive(): Boolean {
        return touchActive
    }

    override fun wasRecentlyTouched(windowMs: Long): Boolean {
        val now = System.currentTimeMillis()

        return touchActive ||
                (now - lastTouchDownAt) <= windowMs ||
                (now - lastTouchMoveAt) <= windowMs ||
                (now - lastTouchUpAt) <= windowMs
    }

    override fun isAvailable(): Boolean {
        return service != null && inputMonitoringActive && !destroyed
    }

    companion object {
        private const val TAG = "AdaptiveHzShizuku"

        private const val USER_SERVICE_TAG = "adaptive_hz_input_monitor"
        private const val PROCESS_NAME_SUFFIX = "input_monitor"
        private const val USER_SERVICE_API_REVISION = 2

        private const val STATUS_CHECK_THROTTLE_MS = 2_000L
        private const val BIND_RETRY_COOLDOWN_MS = 10_000L
    }
}
