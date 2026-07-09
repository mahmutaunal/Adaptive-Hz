package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.os.RemoteException
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Shizuku-backed privileged service responsible for monitoring low-level touchscreen input events.
 */
class InputMonitorUserService : IInputMonitorService.Stub() {

    private val monitoring = AtomicBoolean(false)

    @Volatile
    private var monitorProcess: Process? = null

    @Volatile
    private var monitorThread: Thread? = null

    @Volatile
    private var lastMoveCallbackAt = 0L

    /**
     * Executes shell commands inside the privileged Shizuku process.
     */
    override fun runCommand(command: String?): String {
        if (command.isNullOrBlank()) return ""

        var process: Process? = null

        return try {
            process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()

            val readerThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (true) {
                            line = reader.readLine() ?: break
                            output.append(line).append('\n')

                            if (output.length >= MAX_COMMAND_OUTPUT_CHARS) {
                                break
                            }
                        }
                    }
                }
            }.apply {
                name = "AdaptiveHzCommandReader"
                isDaemon = true
                start()
            }

            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (!finished) {
                Log.w(TAG, "runCommand timed out: $command")
                process.destroySafely()
                return ""
            }

            readerThread.join(READER_JOIN_TIMEOUT_MS)

            output.toString().take(MAX_COMMAND_OUTPUT_CHARS)
        } catch (t: Throwable) {
            Log.e(TAG, "runCommand failed", t)
            ""
        } finally {
            process?.destroySafely()
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

        if (!monitoring.compareAndSet(false, true)) {
            Log.d(TAG, "startMonitoring skipped: already monitoring")
            return
        }

        monitorThread = Thread {
            var process: Process? = null

            try {
                Log.d(TAG, "Starting input monitor: $devicePath")

                process = ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "exec /system/bin/getevent -lt $devicePath"
                )
                    .redirectErrorStream(true)
                    .start()

                monitorProcess = process

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (monitoring.get()) {
                        val line = reader.readLine() ?: break
                        handleInputLine(line, callback)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Input monitor failed", t)
            } finally {
                monitoring.set(false)
                monitorProcess = null

                process?.destroySafely()

                Log.d(TAG, "Input monitor stopped")
            }
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
        Log.d(TAG, "stopMonitoring")

        monitoring.set(false)

        val process = monitorProcess
        monitorProcess = null

        process?.destroySafely()

        val thread = monitorThread
        monitorThread = null

        if (thread != null && thread != Thread.currentThread()) {
            runCatching {
                thread.join(MONITOR_THREAD_JOIN_TIMEOUT_MS)
            }.onFailure {
                Log.e(TAG, "Failed to join monitor thread", it)
            }
        }
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        stopMonitoring()
        exitProcess(0)
    }

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

                if (now - lastMoveCallbackAt >= MOVE_CALLBACK_THROTTLE_MS) {
                    lastMoveCallbackAt = now
                    safeCallback { callback.onTouchMove() }
                }
            }
        }
    }

    private inline fun safeCallback(block: () -> Unit) {
        try {
            block()
        } catch (_: RemoteException) {
            stopMonitoring()
        } catch (t: Throwable) {
            Log.e(TAG, "Callback failed", t)
        }
    }

    private fun Process.destroySafely() {
        runCatching {
            outputStream?.close()
        }

        runCatching {
            inputStream?.close()
        }

        runCatching {
            errorStream?.close()
        }

        runCatching {
            destroy()
        }

        runCatching {
            if (!waitFor(PROCESS_DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                destroyForcibly()
            }
        }
    }

    companion object {
        private const val TAG = "InputMonitorService"

        private const val MOVE_CALLBACK_THROTTLE_MS = 75L

        private const val COMMAND_TIMEOUT_MS = 3_000L
        private const val READER_JOIN_TIMEOUT_MS = 500L
        private const val MONITOR_THREAD_JOIN_TIMEOUT_MS = 1_000L
        private const val PROCESS_DESTROY_TIMEOUT_MS = 500L

        private const val MAX_COMMAND_OUTPUT_CHARS = 8_000
    }
}