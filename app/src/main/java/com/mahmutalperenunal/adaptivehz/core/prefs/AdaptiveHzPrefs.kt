package com.mahmutalperenunal.adaptivehz.core.prefs

import android.content.Context
import androidx.core.content.edit
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode

/**
 * Centralized preferences access for lightweight app state.
 *
 * This class also preserves backward compatibility with the existing preference
 * keys already used across the app.
 */
object AdaptiveHzPrefs {

    private const val PREFS_NAME = "adaptive_hz_prefs"

    const val KEY_ADB_GRANTED = "adb_granted"
    const val KEY_APP_ENABLED = "app_enabled"
    const val KEY_DYNAMIC_ENABLED = "dynamic_enabled"
    const val KEY_MANUAL_TARGET = "manual_target"
    const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    const val KEY_CURRENT_MODE = "current_mode"
    const val KEY_ACCESSIBILITY_LAST_HEARTBEAT = "accessibility_last_heartbeat"
    const val KEY_ACCESSIBILITY_LAST_CONNECTED_AT = "accessibility_last_connected_at"
    const val KEY_ACCESSIBILITY_CONNECTED = "accessibility_connected"
    const val KEY_INITIAL_SETUP_COMPLETED = "initial_setup_completed"
    private const val KEY_APP_PROFILE_PREFIX = "app_profile_"
    const val KEY_DEBUG_FOREGROUND_PACKAGE = "debug_foreground_package"
    const val KEY_DEBUG_LAST_EVENT = "debug_last_event"
    const val KEY_DEBUG_LAST_WRITE = "debug_last_write"
    const val KEY_DEBUG_LAST_WRITE_SUCCESS = "debug_last_write_success"
    const val KEY_DEBUG_LAST_UPDATED_AT = "debug_last_updated_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAdbGranted(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ADB_GRANTED, false)
    }

    fun setAdbGranted(context: Context, granted: Boolean) {
        prefs(context).edit { putBoolean(KEY_ADB_GRANTED, granted) }
    }

    fun isAppEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_APP_ENABLED, true)
    }

    fun setAppEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_APP_ENABLED, enabled) }
    }

    fun isDynamicEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DYNAMIC_ENABLED, false)
    }

    fun setDynamicEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_DYNAMIC_ENABLED, enabled) }
    }

    fun getManualTarget(context: Context): String {
        return prefs(context).getString(KEY_MANUAL_TARGET, "minimum") ?: "minimum"
    }

    fun setManualTarget(context: Context, target: String) {
        prefs(context).edit { putString(KEY_MANUAL_TARGET, target) }
    }

    fun isKeepAliveEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_KEEP_ALIVE_ENABLED, false)
    }

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled) }
    }

    fun getCurrentMode(context: Context): AdaptiveHzMode {
        val raw = prefs(context).getString(KEY_CURRENT_MODE, null)

        if (raw != null) {
            return runCatching { AdaptiveHzMode.valueOf(raw) }
                .getOrDefault(AdaptiveHzMode.OFF)
        }

        // Backward compatibility for existing installs before KEY_CURRENT_MODE existed.
        val appEnabled = isAppEnabled(context)
        if (!appEnabled) return AdaptiveHzMode.OFF

        return if (isDynamicEnabled(context)) {
            AdaptiveHzMode.ADAPTIVE
        } else {
            when (getManualTarget(context)) {
                "maximum" -> AdaptiveHzMode.FORCE_MAX
                "minimum" -> AdaptiveHzMode.FORCE_MIN
                else -> AdaptiveHzMode.OFF
            }
        }
    }

    fun setCurrentMode(context: Context, mode: AdaptiveHzMode) {
        prefs(context).edit { putString(KEY_CURRENT_MODE, mode.name) }
    }

    /**
     * Keeps old preference keys in sync so the existing UI can continue working
     * while the app is migrated to the new mode-based architecture.
     */
    fun syncLegacyStateFromMode(context: Context, mode: AdaptiveHzMode) {
        when (mode) {
            AdaptiveHzMode.OFF -> {
                setAppEnabled(context, false)
                setDynamicEnabled(context, false)
                setManualTarget(context, "system_default")
            }

            AdaptiveHzMode.ADAPTIVE -> {
                setAppEnabled(context, true)
                setDynamicEnabled(context, true)
                setManualTarget(context, "minimum")
            }

            AdaptiveHzMode.FORCE_MIN -> {
                setAppEnabled(context, true)
                setDynamicEnabled(context, false)
                setManualTarget(context, "minimum")
            }

            AdaptiveHzMode.FORCE_MAX -> {
                setAppEnabled(context, true)
                setDynamicEnabled(context, false)
                setManualTarget(context, "maximum")
            }
        }

        setCurrentMode(context, mode)
    }

    fun getAccessibilityLastHeartbeat(context: Context): Long {
        return prefs(context).getLong(KEY_ACCESSIBILITY_LAST_HEARTBEAT, 0L)
    }

    fun setAccessibilityConnected(context: Context, connected: Boolean) {
        prefs(context).edit { putBoolean(KEY_ACCESSIBILITY_CONNECTED, connected) }
    }

    fun markAccessibilityHeartbeat(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit {
            putBoolean(KEY_ACCESSIBILITY_CONNECTED, true)
            putLong(KEY_ACCESSIBILITY_LAST_CONNECTED_AT, now)
            putLong(KEY_ACCESSIBILITY_LAST_HEARTBEAT, now)
        }
    }

    fun markAccessibilityDisconnected(context: Context) {
        prefs(context).edit { putBoolean(KEY_ACCESSIBILITY_CONNECTED, false) }
    }

    fun isInitialSetupCompleted(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_INITIAL_SETUP_COMPLETED, false)
    }

    fun setInitialSetupCompleted(context: Context, completed: Boolean) {
        prefs(context).edit { putBoolean(KEY_INITIAL_SETUP_COMPLETED, completed) }
    }

    fun getAppRefreshProfileMode(
        context: Context,
        packageName: String?
    ): AppRefreshProfileMode {
        if (packageName.isNullOrBlank()) return AppRefreshProfileMode.DEFAULT

        val raw = prefs(context).getString(KEY_APP_PROFILE_PREFIX + packageName, null)
            ?: return AppRefreshProfileMode.DEFAULT

        return runCatching { AppRefreshProfileMode.valueOf(raw) }
            .getOrDefault(AppRefreshProfileMode.DEFAULT)
    }

    fun setAppRefreshProfileMode(
        context: Context,
        packageName: String,
        mode: AppRefreshProfileMode
    ) {
        if (packageName.isBlank()) return

        prefs(context).edit {
            if (mode == AppRefreshProfileMode.DEFAULT) {
                remove(KEY_APP_PROFILE_PREFIX + packageName)
            } else {
                putString(KEY_APP_PROFILE_PREFIX + packageName, mode.name)
            }
        }
    }

    fun updateDebugForegroundPackage(context: Context, packageName: String?) {
        prefs(context).edit {
            putString(KEY_DEBUG_FOREGROUND_PACKAGE, packageName.orEmpty())
            putLong(KEY_DEBUG_LAST_UPDATED_AT, System.currentTimeMillis())
        }
    }

    fun updateDebugLastEvent(context: Context, eventName: String, packageName: String?) {
        prefs(context).edit {
            putString(KEY_DEBUG_LAST_EVENT, "$eventName / ${packageName.orEmpty()}")
            putLong(KEY_DEBUG_LAST_UPDATED_AT, System.currentTimeMillis())
        }
    }

    fun updateDebugLastWrite(
        context: Context,
        label: String,
        success: Boolean
    ) {
        prefs(context).edit {
            putString(KEY_DEBUG_LAST_WRITE, label)
            putBoolean(KEY_DEBUG_LAST_WRITE_SUCCESS, success)
            putLong(KEY_DEBUG_LAST_UPDATED_AT, System.currentTimeMillis())
        }
    }

    fun getDebugForegroundPackage(context: Context): String {
        return prefs(context).getString(KEY_DEBUG_FOREGROUND_PACKAGE, "") ?: ""
    }

    fun getDebugLastEvent(context: Context): String {
        return prefs(context).getString(KEY_DEBUG_LAST_EVENT, "") ?: ""
    }

    fun getDebugLastWrite(context: Context): String {
        return prefs(context).getString(KEY_DEBUG_LAST_WRITE, "") ?: ""
    }

    fun wasDebugLastWriteSuccess(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DEBUG_LAST_WRITE_SUCCESS, false)
    }

    fun getDebugLastUpdatedAt(context: Context): Long {
        return prefs(context).getLong(KEY_DEBUG_LAST_UPDATED_AT, 0L)
    }
}