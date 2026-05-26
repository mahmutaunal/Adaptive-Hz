package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
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

    // Receives touch state updates from the Shizuku UserService process.
    private val inputEventCallback =
        object : IInputEventCallback.Stub() {

            override fun onTouchDown() {
                inputMonitoringActive = true
                touchActive = true
                lastTouchDownAt = System.currentTimeMillis()
                //Log.d(TAG, "Touch DOWN")
            }

            override fun onTouchMove() {
                inputMonitoringActive = true
                lastTouchMoveAt = System.currentTimeMillis()
                //Log.d(TAG, "Touch MOVE")
            }

            override fun onTouchUp() {
                inputMonitoringActive = true
                touchActive = false
                lastTouchUpAt = System.currentTimeMillis()
                //Log.d(TAG, "Touch UP")
            }
        }

    private var service: IInputMonitorService? = null

    // Permission flow is asynchronous; bind the UserService only after Shizuku grants access.
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener

            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "permissionResult granted=$granted")

            if (granted) {
                bindUserService()
            }
        }

    // Keeps the local binder reference in sync with the lifecycle of the Shizuku UserService.
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "UserService connected")
            service = IInputMonitorService.Stub.asInterface(binder)
            detectTouchscreenDeviceAndStartMonitoring()
            bindingInProgress = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "UserService disconnected")
            bindingInProgress = false
            service = null
            touchActive = false
            inputMonitoringActive = false
        }
    }

    /**
     * Validates Shizuku availability and permission state before binding the input monitor service.
     */
    fun checkStatus() {
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }

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

        Log.d(TAG, "Shizuku is ready")

        bindUserService()
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
        Log.d(TAG, "Binding UserService...")

        // Prevent duplicate bind requests while Android is still delivering the connection callback.
        if (bindingInProgress || service != null) {
            Log.d(TAG, "bind skipped: already binding/connected")
            return
        }

        bindingInProgress = true

        runCatching {
            Shizuku.bindUserService(userServiceArgs(), connection)
            Log.d(TAG, "bindUserService called")
        }.onFailure {
            bindingInProgress = false
            Log.e(TAG, "bindUserService failed", it)
        }
    }

    private fun userServiceArgs(): UserServiceArgs {
        return UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                InputMonitorUserService::class.java.name
            )
        )
            .tag("adaptive_hz_input_monitor")
            .daemon(false)
            .processNameSuffix("input_monitor")
            .debuggable(BuildConfig.DEBUG)
            .version(VERSION)
    }

    /**
     * Finds the physical touchscreen input node and starts monitoring raw touch events from it.
     */
    private fun detectTouchscreenDeviceAndStartMonitoring() {
        runCatching {
            val result = service?.runCommand("getevent -lp").orEmpty()
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

    /**
     * Parses `getevent -lp` output and returns the most likely direct touchscreen device path.
     */
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

        // Some devices do not expose INPUT_PROP_DIRECT consistently, so keep a conservative fallback.
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
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }

        runCatching {
            service?.stopMonitoring()
        }

        runCatching {
            Shizuku.unbindUserService(userServiceArgs(), connection, true)
        }

        service = null
        touchActive = false
        inputMonitoringActive = false
        bindingInProgress = false
    }

    override fun isTouchActive(): Boolean {
        return touchActive
    }

    // Treat short-lived down/move/up events as interaction so refresh rate does not drop immediately.
    override fun wasRecentlyTouched(windowMs: Long): Boolean {
        val now = System.currentTimeMillis()

        return touchActive ||
                (now - lastTouchDownAt) <= windowMs ||
                (now - lastTouchMoveAt) <= windowMs ||
                (now - lastTouchUpAt) <= windowMs
    }

    override fun isAvailable(): Boolean {
        return service != null && inputMonitoringActive
    }

    companion object {
        private const val TAG = "AdaptiveHzShizuku"
        private const val REQUEST_CODE = 6201
        private const val VERSION = 3
    }
}