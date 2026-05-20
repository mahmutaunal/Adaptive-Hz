package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.os.RemoteException
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Shizuku-backed privileged service responsible for monitoring low-level touchscreen input events.
 */
class InputMonitorUserService : IInputMonitorService.Stub() {

    @Volatile
    private var monitoring = false

    private var monitorProcess: Process? = null
    private var monitorThread: Thread? = null

    private var lastMoveCallbackAt = 0L

    /**
     * Executes shell commands inside the privileged Shizuku process.
     */
    override fun runCommand(command: String?): String {
        if (command.isNullOrBlank()) return ""

        return runCatching {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream))
                .use { it.readText() }

            process.waitFor()
            output.take(8_000)
        }.getOrElse {
            Log.e(TAG, "runCommand failed", it)
            it.stackTraceToString()
        }
    }

    /**
     * Starts streaming raw input events from the selected touchscreen device node.
     */
    override fun startMonitoring(
        devicePath: String?,
        callback: IInputEventCallback?
    ) {
        if (devicePath.isNullOrBlank() || callback == null) return
        if (monitoring) return

        monitoring = true

        monitorThread = Thread {
            runCatching {
                Log.d(TAG, "Starting input monitor: $devicePath")

                // Use exec so the spawned shell process is fully replaced by getevent.
                val process = ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "exec /system/bin/getevent -lt $devicePath"
                )
                    .redirectErrorStream(true)
                    .start()

                monitorProcess = process

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (monitoring) {
                        val line = reader.readLine() ?: break
                        handleInputLine(line, callback)
                    }
                }
            }.onFailure {
                Log.e(TAG, "Input monitor failed", it)
            }

            monitoring = false
            Log.d(TAG, "Input monitor stopped")
        }.apply {
            name = "AdaptiveHzInputMonitor"
            isDaemon = false
            start()
        }
    }

    /**
     * Stops the active getevent process and releases monitoring resources.
     */
    override fun stopMonitoring() {
        monitoring = false

        runCatching {
            monitorProcess?.destroy()
        }

        monitorProcess = null
        monitorThread = null

        Log.d(TAG, "stopMonitoring")
    }

    override fun destroy() {
        stopMonitoring()
        exitProcess(0)
    }

    /**
     * Maps raw getevent output into simplified touch lifecycle callbacks.
     */
    private fun handleInputLine(
        line: String,
        callback: IInputEventCallback
    ) {
        when {
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                safeCallback { callback.onTouchDown() }
            }

            line.contains("BTN_TOUCH") && line.contains("UP") -> {
                safeCallback { callback.onTouchUp() }
            }

            line.contains("ABS_MT_TRACKING_ID") &&
                    line.contains("ffffffff", ignoreCase = true) -> {
                safeCallback { callback.onTouchUp() }
            }

            line.contains("ABS_MT_POSITION_X") ||
                    line.contains("ABS_MT_POSITION_Y") -> {
                val now = System.currentTimeMillis()

                // Throttle move events to avoid excessive Binder traffic during continuous gestures.
                if (now - lastMoveCallbackAt >= MOVE_CALLBACK_THROTTLE_MS) {
                    lastMoveCallbackAt = now
                    safeCallback { callback.onTouchMove() }
                }
            }
        }
    }

    /**
     * Prevents Binder callback failures from crashing the monitoring loop.
     */
    private inline fun safeCallback(block: () -> Unit) {
        try {
            block()
        } catch (_: RemoteException) {
            stopMonitoring()
        } catch (t: Throwable) {
            Log.e(TAG, "Callback failed", t)
        }
    }

    companion object {
        private const val TAG = "InputMonitorService"
        // Continuous move events can be extremely noisy on high refresh rate touch panels.
        private const val MOVE_CALLBACK_THROTTLE_MS = 75L
    }
}