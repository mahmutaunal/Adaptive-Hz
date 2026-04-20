package com.mahmutalperenunal.adaptivehz.core.engine

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