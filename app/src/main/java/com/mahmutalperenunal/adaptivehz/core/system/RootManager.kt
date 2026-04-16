package com.mahmutalperenunal.adaptivehz.core.system

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.BufferedReader

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
        return ContextCompat.checkSelfPermission(
            context,
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
        if (hasWriteSecureSettings(context)) {
            return RootState.Available
        }

        val packageName = context.packageName
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

        return if (hasWriteSecureSettings(context)) {
            RootState.Available
        } else {
            RootState.Failed("Permission command completed but permission is still not granted.")
        }
    }

    private fun runSuCommand(command: String): CommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()

            CommandResult(
                success = exitCode == 0,
                exitCode = exitCode,
                stdout = stdout.trim(),
                stderr = stderr.trim()
            )
        } catch (t: Throwable) {
            CommandResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = t.message.orEmpty()
            )
        }
    }
}