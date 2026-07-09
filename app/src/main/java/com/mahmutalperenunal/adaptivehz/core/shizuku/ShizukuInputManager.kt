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

class ShizukuInputManager : InteractionSignalProvider {

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

    private val lock = Any()

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
            .version(VERSION)
    }

    private val inputEventCallback =
        object : IInputEventCallback.Stub() {

            override fun onTouchDown() {
                inputMonitoringActive = true
                touchActive = true
                lastTouchDownAt = System.currentTimeMillis()
            }

            override fun onTouchMove() {
                inputMonitoringActive = true
                lastTouchMoveAt = System.currentTimeMillis()
            }

            override fun onTouchUp() {
                inputMonitoringActive = true
                touchActive = false
                lastTouchUpAt = System.currentTimeMillis()
            }
        }

    private var service: IInputMonitorService? = null

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
            if (destroyed) return@OnRequestPermissionResultListener

            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "permissionResult granted=$granted")

            if (granted) {
                bindUserService()
            }
        }

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "UserService connected name=$name")

            synchronized(lock) {
                if (destroyed) {
                    Log.w(TAG, "Service connected after destroy, unbinding immediately")
                    runCatching {
                        Shizuku.unbindUserService(userServiceArgs, this, true)
                    }
                    return
                }

                service = IInputMonitorService.Stub.asInterface(binder)
                bound = true
                bindingInProgress = false
            }

            detectTouchscreenDeviceAndStartMonitoring()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "UserService disconnected name=$name")

            synchronized(lock) {
                bindingInProgress = false
                bound = false
                service = null
                touchActive = false
                inputMonitoringActive = false
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "UserService binding died name=$name")

            synchronized(lock) {
                bindingInProgress = false
                bound = false
                service = null
                touchActive = false
                inputMonitoringActive = false
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "UserService null binding name=$name")

            synchronized(lock) {
                bindingInProgress = false
                bound = false
                service = null
                touchActive = false
                inputMonitoringActive = false
            }
        }
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
            if (bindingInProgress) return

            if (now - lastStatusCheckAt < STATUS_CHECK_THROTTLE_MS) {
                return
            }

            lastStatusCheckAt = now
        }

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
            requestPermission()
            return
        }

        bindUserService()
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

    private fun requestPermission() {
        runCatching {
            Shizuku.requestPermission(REQUEST_CODE)
            Log.d(TAG, "Permission requested")
        }.onFailure {
            Log.e(TAG, "Permission request failed", it)
        }
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
            val result = remote.runCommand("getevent -lp")
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

        unregisterPermissionListener()

        val remote = synchronized(lock) {
            val current = service
            service = null
            inputMonitoringActive = false
            touchActive = false
            bindingInProgress = false
            bound = false
            current
        }

        runCatching {
            remote?.stopMonitoring()
        }.onFailure {
            Log.e(TAG, "stopMonitoring failed during destroy", it)
        }

        runCatching {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
            Log.d(TAG, "unbindUserService called")
        }.onFailure {
            Log.e(TAG, "unbindUserService failed", it)
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

        private const val REQUEST_CODE = 6201

        /**
         * Increment this whenever the UserService lifecycle changes significantly.
         *
         * Shizuku uses versioning to recreate old UserService instances when needed.
         */
        private const val VERSION = 4

        private const val USER_SERVICE_TAG = "adaptive_hz_input_monitor"
        private const val PROCESS_NAME_SUFFIX = "input_monitor"

        private const val STATUS_CHECK_THROTTLE_MS = 2_000L
        private const val BIND_RETRY_COOLDOWN_MS = 10_000L
    }
}