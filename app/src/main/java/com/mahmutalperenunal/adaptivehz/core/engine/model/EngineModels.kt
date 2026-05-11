package com.mahmutalperenunal.adaptivehz.core.engine.model

/**
 * Represents a single system setting write operation.
 *
 * Used by the engine to apply refresh rate related values via ADB/root.
 */
data class SettingWrite(
    val key: String,
    val intValue: Int,
    val label: String = "$key=$intValue"
)

/**
 * Represents the app's current refresh-rate control mode.
 *
 * OFF         -> App behavior disabled, system default/adaptive restored.
 * ADAPTIVE    -> Touch-aware adaptive behavior is active.
 * FORCE_MIN   -> Refresh rate locked to minimum.
 * FORCE_MAX   -> Refresh rate locked to maximum.
 */
enum class AdaptiveHzMode {
    OFF,
    ADAPTIVE,
    FORCE_MIN,
    FORCE_MAX
}

/**
 * Per-app refresh behavior.
 *
 * DEFAULT:
 * Uses global AdaptiveHz mode.
 *
 * ADAPTIVE:
 * AdaptiveHz controls this app with touch-aware behavior.
 *
 * FORCE_MIN:
 * Locks this app to minimum refresh rate.
 *
 * FORCE_MAX:
 * Locks this app to maximum refresh rate.
 *
 * RESPECT_APP:
 * AdaptiveHz does not write refresh settings while this app is foreground.
 * This allows the app/system/vendor refresh behavior to work naturally.
 *
 * DISABLED:
 * AdaptiveHz ignores this app and applies LOW as a safe state once.
 */
enum class AppRefreshProfileMode {
    DEFAULT,
    ADAPTIVE,
    FORCE_MIN,
    FORCE_MAX,
    RESPECT_APP,
    DISABLED
}