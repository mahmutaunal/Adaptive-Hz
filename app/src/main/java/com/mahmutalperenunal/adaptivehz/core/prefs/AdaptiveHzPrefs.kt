package com.mahmutalperenunal.adaptivehz.core.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mahmutalperenunal.adaptivehz.core.engine.model.AdaptiveHzMode
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode
import com.mahmutalperenunal.adaptivehz.core.support.SupportPromptPolicy

/**
 * Centralized preferences access for lightweight app state.
 *
 * This class also preserves backward compatibility with the existing preference
 * keys already used across the app.
 */

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage {
    SYSTEM,
    EN,
    TR,
    ES,
    PT_BR
}

object AdaptiveHzPrefs {

    private const val PREFS_NAME = "adaptive_hz_prefs"

    const val KEY_ADB_GRANTED = "adb_granted"
    const val KEY_APP_ENABLED = "app_enabled"
    const val KEY_DYNAMIC_ENABLED = "dynamic_enabled"
    const val KEY_MANUAL_TARGET = "manual_target"
    const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    const val KEY_KEEP_ACTIVE_DURING_BATTERY_SAVER = "keep_active_during_battery_saver"
    const val KEY_CURRENT_MODE = "current_mode"
    const val KEY_ACCESSIBILITY_LAST_HEARTBEAT = "accessibility_last_heartbeat"
    const val KEY_ACCESSIBILITY_LAST_CONNECTED_AT = "accessibility_last_connected_at"
    const val KEY_ACCESSIBILITY_CONNECTED = "accessibility_connected"
    const val KEY_ACCESSIBILITY_LAST_HEALTH_STATE = "accessibility_last_health_state"
    const val KEY_ACCESSIBILITY_LAST_RECOVERY_NOTIFIED_AT = "accessibility_last_recovery_notified_at"
    const val KEY_INITIAL_SETUP_COMPLETED = "initial_setup_completed"
    private const val KEY_APP_PROFILE_PREFIX = "app_profile_"
    const val KEY_DEBUG_FOREGROUND_PACKAGE = "debug_foreground_package"
    const val KEY_DEBUG_LAST_EVENT = "debug_last_event"
    const val KEY_DEBUG_LAST_WRITE = "debug_last_write"
    const val KEY_DEBUG_LAST_WRITE_SUCCESS = "debug_last_write_success"
    const val KEY_DEBUG_LAST_UPDATED_AT = "debug_last_updated_at"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_APP_LANGUAGE = "app_language"
    const val KEY_INTERACTION_DROP_DELAY_MS = "interaction_drop_delay_ms"
    const val KEY_QUICK_SETTINGS_TILE_ADDED = "quick_settings_tile_added"
    const val KEY_QUICK_ACCESS_DISCOVERY_SHOWN = "quick_access_discovery_shown"
    const val KEY_UPDATE_CHECK_CHOICE_MADE = "update_check_choice_made"
    const val KEY_AUTOMATIC_UPDATE_CHECKS = "automatic_update_checks"
    const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
    const val KEY_LAST_PROMPTED_RELEASE_TAG = "last_prompted_release_tag"
    const val KEY_SUPPORT_PROMPT_SHOWN = "support_prompt_shown"
    const val KEY_SUPPORT_SESSION_COUNT = "support_session_count"
    const val KEY_SUPPORT_DISTINCT_DAY_COUNT = "support_distinct_day_count"
    const val KEY_SUPPORT_LAST_SESSION_AT = "support_last_session_at"
    const val KEY_SUPPORT_LAST_SESSION_DAY = "support_last_session_day"
    const val DEFAULT_INTERACTION_DROP_DELAY_MS = 2000L
    const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    val INTERACTION_DROP_DELAY_OPTIONS_MS = listOf(250L, 500L, 750L, 1000L, 1500L, 2000L, 2500L, 3000L, 3500L, 4000L, 4500L)

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

    fun shouldKeepActiveDuringBatterySaver(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_KEEP_ACTIVE_DURING_BATTERY_SAVER, false)
    }

    fun setKeepActiveDuringBatterySaver(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_KEEP_ACTIVE_DURING_BATTERY_SAVER, enabled)
        }
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

    fun isQuickSettingsTileAdded(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_QUICK_SETTINGS_TILE_ADDED, false)
    }

    fun setQuickSettingsTileAdded(context: Context, added: Boolean) {
        prefs(context).edit { putBoolean(KEY_QUICK_SETTINGS_TILE_ADDED, added) }
    }

    fun observeQuickSettingsTile(
        context: Context,
        onChanged: (Boolean) -> Unit
    ): () -> Unit {
        val appContext = context.applicationContext
        val sharedPreferences = prefs(appContext)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_QUICK_SETTINGS_TILE_ADDED) {
                onChanged(isQuickSettingsTileAdded(appContext))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        return {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun shouldShowQuickAccessDiscovery(context: Context): Boolean {
        return !prefs(context).getBoolean(KEY_QUICK_ACCESS_DISCOVERY_SHOWN, false)
    }

    fun markQuickAccessDiscoveryShown(context: Context) {
        prefs(context).edit { putBoolean(KEY_QUICK_ACCESS_DISCOVERY_SHOWN, true) }
    }

    fun hasMadeUpdateCheckChoice(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_UPDATE_CHECK_CHOICE_MADE, false)
    }

    fun isAutomaticUpdateCheckEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, false)
    }

    fun setAutomaticUpdateChecksEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_UPDATE_CHECK_CHOICE_MADE, true)
            putBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, enabled)
        }
    }

    fun shouldRunAutomaticUpdateCheck(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isAutomaticUpdateCheckEnabled(context)) return false
        val lastCheck = prefs(context).getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
        return lastCheck == 0L || nowMillis - lastCheck >= UPDATE_CHECK_INTERVAL_MS
    }

    fun markUpdateCheckAttempt(
        context: Context,
        atMillis: Long = System.currentTimeMillis()
    ) {
        prefs(context).edit { putLong(KEY_LAST_UPDATE_CHECK_AT, atMillis) }
    }

    fun wasReleaseAlreadyPrompted(context: Context, tag: String): Boolean {
        return prefs(context).getString(KEY_LAST_PROMPTED_RELEASE_TAG, null) == tag
    }

    fun markReleasePrompted(context: Context, tag: String) {
        prefs(context).edit { putString(KEY_LAST_PROMPTED_RELEASE_TAG, tag) }
    }

    /** Records a qualified session without analytics or network access. */
    fun recordSupportSession(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        localEpochDay: Long,
    ) {
        val storage = prefs(context)
        val lastSessionAt = storage.getLong(KEY_SUPPORT_LAST_SESSION_AT, 0L)
        if (
            lastSessionAt != 0L &&
            nowMillis - lastSessionAt < SupportPromptPolicy.MIN_SESSION_GAP_MS
        ) {
            return
        }

        val lastSessionDay = storage.getLong(KEY_SUPPORT_LAST_SESSION_DAY, Long.MIN_VALUE)
        val distinctDays = storage.getInt(KEY_SUPPORT_DISTINCT_DAY_COUNT, 0) +
            if (lastSessionDay != localEpochDay) 1 else 0

        storage.edit {
            putInt(KEY_SUPPORT_SESSION_COUNT, getSupportSessionCount(context) + 1)
            putInt(KEY_SUPPORT_DISTINCT_DAY_COUNT, distinctDays)
            putLong(KEY_SUPPORT_LAST_SESSION_AT, nowMillis)
            putLong(KEY_SUPPORT_LAST_SESSION_DAY, localEpochDay)
        }
    }

    fun shouldShowSupportPrompt(context: Context): Boolean {
        val storage = prefs(context)
        return SupportPromptPolicy.isEligible(
            promptShown = storage.getBoolean(KEY_SUPPORT_PROMPT_SHOWN, false),
            sessionCount = getSupportSessionCount(context),
            distinctDayCount = storage.getInt(KEY_SUPPORT_DISTINCT_DAY_COUNT, 0),
        )
    }

    fun markSupportPromptShown(context: Context) {
        prefs(context).edit { putBoolean(KEY_SUPPORT_PROMPT_SHOWN, true) }
    }

    private fun getSupportSessionCount(context: Context): Int {
        return prefs(context).getInt(KEY_SUPPORT_SESSION_COUNT, 0)
    }

    /**
     * Observes persisted mode changes made by any in-process surface, including
     * the Quick Settings tile, widget and foreground notification.
     *
     * Returns a cleanup callback that must be invoked when observation stops.
     */
    fun observeCurrentMode(
        context: Context,
        onChanged: (AdaptiveHzMode) -> Unit
    ): () -> Unit {
        val appContext = context.applicationContext
        val sharedPreferences = prefs(appContext)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CURRENT_MODE) {
                onChanged(getCurrentMode(appContext))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        return {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
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

    fun getAccessibilityLastHealthState(context: Context): String {
        return prefs(context).getString(KEY_ACCESSIBILITY_LAST_HEALTH_STATE, "") ?: ""
    }

    fun setAccessibilityLastHealthState(context: Context, state: String) {
        prefs(context).edit {
            putString(KEY_ACCESSIBILITY_LAST_HEALTH_STATE, state)
        }
    }

    fun getAccessibilityLastRecoveryNotifiedAt(context: Context): Long {
        return prefs(context).getLong(KEY_ACCESSIBILITY_LAST_RECOVERY_NOTIFIED_AT, 0L)
    }

    fun setAccessibilityLastRecoveryNotifiedAt(context: Context, timestamp: Long) {
        prefs(context).edit {
            putLong(KEY_ACCESSIBILITY_LAST_RECOVERY_NOTIFIED_AT, timestamp)
        }
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

        val key = KEY_APP_PROFILE_PREFIX + packageName

        val raw = prefs(context).getString(key, null)
            ?: return AppRefreshProfileMode.DEFAULT

        val migrated = when (raw) {
            "DEFAULT" -> AppRefreshProfileMode.DEFAULT
            "RESPECT_APP" -> AppRefreshProfileMode.SYSTEM_CONTROLLED
            "DISABLED" -> AppRefreshProfileMode.SYSTEM_CONTROLLED
            "ADAPTIVE" -> AppRefreshProfileMode.DEFAULT
            "FORCE_MIN" -> AppRefreshProfileMode.FORCE_MIN
            "FORCE_MAX" -> AppRefreshProfileMode.FORCE_MAX
            "SYSTEM_CONTROLLED" -> AppRefreshProfileMode.SYSTEM_CONTROLLED
            else -> AppRefreshProfileMode.DEFAULT
        }

        if (raw != migrated.name) {
            prefs(context).edit {
                if (migrated == AppRefreshProfileMode.DEFAULT) {
                    remove(key)
                } else {
                    putString(key, migrated.name)
                }
            }
        }

        return migrated
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

    fun getInteractionDropDelayMs(context: Context): Long {
        val value = prefs(context).getLong(
            KEY_INTERACTION_DROP_DELAY_MS,
            DEFAULT_INTERACTION_DROP_DELAY_MS
        )

        return INTERACTION_DROP_DELAY_OPTIONS_MS.minBy { kotlin.math.abs(it - value) }
    }

    fun setInteractionDropDelayMs(context: Context, delayMs: Long) {
        val normalized = INTERACTION_DROP_DELAY_OPTIONS_MS
            .minBy { kotlin.math.abs(it - delayMs) }

        prefs(context).edit {
            putLong(KEY_INTERACTION_DROP_DELAY_MS, normalized)
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

    fun getThemeMode(context: Context): AppThemeMode {
        val raw = prefs(context).getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(raw ?: AppThemeMode.SYSTEM.name) }
            .getOrDefault(AppThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        prefs(context).edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun getAppLanguage(context: Context): AppLanguage {
        val raw = prefs(context).getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.name)
        return runCatching { AppLanguage.valueOf(raw ?: AppLanguage.SYSTEM.name) }
            .getOrDefault(AppLanguage.SYSTEM)
    }

    fun setAppLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit { putString(KEY_APP_LANGUAGE, language.name) }
    }
}
