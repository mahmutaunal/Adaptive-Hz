package com.mahmutalperenunal.adaptivehz.core.system

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

object RootManager {

    sealed class RootState {
        data object Available : RootState()
        data object Unavailable : RootState()
        data object Denied : RootState()
        data class Failed(val reason: String? = null) : RootState()
    }

    data class CommandResult(
        val success: Boolean,
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    fun hasWriteSecureSettings(context: Context): Boolean {
        val appContext = context.applicationContext
        return ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether a root shell can actually be opened.
     */
    fun getRootState(): RootState {
        val result = runSuCommand("id")
        if (result.success) {
            val output = result.stdout.lowercase()
            return if ("uid=0" in output) {
                RootState.Available
            } else {
                RootState.Failed(output)
            }
        }

        val errorText = buildString {
            append(result.stderr)
            if (result.stdout.isNotBlank()) {
                if (isNotBlank()) append(" | ")
                append(result.stdout)
            }
        }.lowercase()

        return when {
            "permission denied" in errorText -> RootState.Denied
            "not found" in errorText -> RootState.Unavailable
            result.exitCode == 255 -> RootState.Denied
            else -> RootState.Unavailable
        }
    }

    /**
     * Attempts to grant WRITE_SECURE_SETTINGS to this app using root.
     */
    fun grantWriteSecureSettings(context: Context): RootState {
        val appContext = context.applicationContext
        if (hasWriteSecureSettings(appContext)) {
            return RootState.Available
        }

        val packageName = appContext.packageName
        val command = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        val result = runSuCommand(command)

        if (!result.success) {
            val errorText = buildString {
                append(result.stderr)
                if (result.stdout.isNotBlank()) {
                    if (isNotBlank()) append(" | ")
                    append(result.stdout)
                }
            }

            return when {
                errorText.contains("denied", ignoreCase = true) -> RootState.Denied
                else -> RootState.Failed(errorText.ifBlank { null })
            }
        }

        return if (hasWriteSecureSettings(appContext)) {
            RootState.Available
        } else {
            RootState.Failed("Permission command completed but permission is still not granted.")
        }
    }

    private fun runSuCommand(command: String): CommandResult {
        var process: Process? = null
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        return try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()

            val stdoutThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (true) {
                            line = reader.readLine() ?: break
                            if (stdout.length < MAX_COMMAND_OUTPUT_CHARS) {
                                stdout.append(line).append('\n')
                            }
                        }
                    }
                }
            }.apply {
                name = "AdaptiveHzRootStdoutReader"
                isDaemon = true
                start()
            }

            val stderrThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().use { reader ->
                        var line: String?
                        while (true) {
                            line = reader.readLine() ?: break
                            if (stderr.length < MAX_COMMAND_OUTPUT_CHARS) {
                                stderr.append(line).append('\n')
                            }
                        }
                    }
                }
            }.apply {
                name = "AdaptiveHzRootStderrReader"
                isDaemon = true
                start()
            }

            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (!finished) {
                process.destroySafely()
                stdoutThread.join(READER_JOIN_TIMEOUT_MS)
                stderrThread.join(READER_JOIN_TIMEOUT_MS)

                return CommandResult(
                    success = false,
                    exitCode = -1,
                    stdout = stdout.toString().trim(),
                    stderr = "Command timed out"
                )
            }

            stdoutThread.join(READER_JOIN_TIMEOUT_MS)
            stderrThread.join(READER_JOIN_TIMEOUT_MS)

            val exitCode = process.exitValue()

            CommandResult(
                success = exitCode == 0,
                exitCode = exitCode,
                stdout = stdout.toString().trim(),
                stderr = stderr.toString().trim()
            )
        } catch (t: Throwable) {
            CommandResult(
                success = false,
                exitCode = -1,
                stdout = stdout.toString().trim(),
                stderr = t.message.orEmpty()
            )
        } finally {
            process?.destroySafely()
        }
    }

    private fun Process.destroySafely() {
        runCatching { outputStream.close() }
        runCatching { inputStream.close() }
        runCatching { errorStream.close() }

        runCatching { destroy() }

        runCatching {
            if (!waitFor(PROCESS_DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                destroyForcibly()
            }
        }
    }

    private const val COMMAND_TIMEOUT_MS = 5_000L
    private const val READER_JOIN_TIMEOUT_MS = 500L
    private const val PROCESS_DESTROY_TIMEOUT_MS = 500L
    private const val MAX_COMMAND_OUTPUT_CHARS = 8_000
}