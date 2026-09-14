package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuAccessState {
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    READY
}

object ShizukuAccess {
    const val UI_PERMISSION_REQUEST_CODE = 42_002

    fun readState(): ShizukuAccessState {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            return ShizukuAccessStateResolver.resolve(
                running = false,
                permissionGranted = false
            )
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return ShizukuAccessStateResolver.resolve(running, granted)
    }

    /** Requests access after the caller has confirmed that the Shizuku binder is running. */
    fun requestPermission(): Boolean {
        return runCatching {
            Shizuku.requestPermission(UI_PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }
}

internal object ShizukuAccessStateResolver {
    fun resolve(running: Boolean, permissionGranted: Boolean): ShizukuAccessState = when {
        !running -> ShizukuAccessState.NOT_RUNNING
        permissionGranted -> ShizukuAccessState.READY
        else -> ShizukuAccessState.PERMISSION_REQUIRED
    }
}
