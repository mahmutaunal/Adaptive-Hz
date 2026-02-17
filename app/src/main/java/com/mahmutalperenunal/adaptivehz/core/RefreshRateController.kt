package com.mahmutalperenunal.adaptivehz.core

import android.content.ContentValues
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Helper that converts the screen refresh rate to an adaptive range of Min–Max Hz via system settings
 * on unsupported Samsung Galaxy devices.
 *
 * MIN_REFRESH_RATE and PEAK_REFRESH_RATE are defined in the AOSP documentation.
 * refresh_rate_mode is a hidden secure key that can also be changed via ADB on Samsung devices:
 * 0 = Normal, 1 = Adaptive, 2 = High, etc.
 */
object RefreshRateController {

    // Key used for adaptive/normal/high on Samsung devices
    private const val KEY_REFRESH_MODE = "refresh_rate_mode"

    // Key used for adaptive/normal/high on Xiaomi/HyperOS devices
    private const val KEY_XIAOMI_REFRESH_RATE = "miui_refresh_rate"

    // Cache resolved Xiaomi min/max values to avoid recalculating on every call
    private var cachedXiaomiMinMax: Pair<Int, Int>? = null

    /**
     * Resolves the supported minimum and maximum refresh rates on Xiaomi / HyperOS devices
     * by querying the display's supported modes at runtime.
     *
     * @return Pair(minHz, maxHz). Falls back to 60–120 if detection fails.
     */
    private fun resolveXiaomiMinMax(context: Context): Pair<Int, Int> {
        cachedXiaomiMinMax?.let { return it }

        val rates: List<Int> = try {
            val display = context.display
            display.supportedModes
                .map { it.refreshRate.toInt() }
                .distinct()
                .sorted()
        } catch (_: Exception) {
            emptyList()
        }

        // HyperOS devices may expose 30 Hz, but some panels flicker at 30.
        // Clamp the minimum to 60 Hz for a better user experience.
        val rawMin = rates.firstOrNull() ?: 60
        val rawMax = rates.lastOrNull() ?: 120

        val minHz = if (rawMin < 60) 60 else rawMin
        val maxHz = if (rawMax < minHz) minHz else rawMax

        return (minHz to maxHz).also { cachedXiaomiMinMax = it }
    }

    /**
     * If you want, set the device to a fixed Minimum Hz.
     */
    fun applyForceMinimum(context: Context) {
        val cr = context.contentResolver
        try {
            val (key, value) = when (DeviceVendorDetector.detect()) {
                DeviceVendor.XIAOMI -> {
                    // On HyperOS, we must explicitly write the target Hz value.
                    // Use the device-supported minimum refresh rate (e.g. 30 or 60).
                    val (minHz, _) = resolveXiaomiMinMax(context)
                    KEY_XIAOMI_REFRESH_RATE to minHz
                }
                DeviceVendor.SAMSUNG -> KEY_REFRESH_MODE to 0 // Normal / system minimum
                DeviceVendor.OTHER -> KEY_REFRESH_MODE to 0 // Fallback
            }

            Settings.Secure.putInt(
                cr,
                key,
                value
            )
        } catch (e: Exception) {
            Log.e(ContentValues.TAG, "Failed to set Normal mode", e)
        }
    }

    /**
     * If you want, set the device to a fixed Maximum Hz.
     */
    fun applyForceMaximum(context: Context) {
        val cr = context.contentResolver
        try {
            val (key, value) = when (DeviceVendorDetector.detect()) {
                DeviceVendor.XIAOMI -> {
                    // On HyperOS, explicitly switch to the device-supported maximum refresh rate
                    val (_, maxHz) = resolveXiaomiMinMax(context)
                    KEY_XIAOMI_REFRESH_RATE to maxHz
                }
                DeviceVendor.SAMSUNG -> KEY_REFRESH_MODE to 2 // High / system maximum
                DeviceVendor.OTHER -> KEY_REFRESH_MODE to 2 // Fallback
            }

            Settings.Secure.putInt(
                cr,
                key,
                value
            )
        } catch (e: Exception) {
            Log.e(ContentValues.TAG, "Failed to set High mode", e)
        }
    }

    /**
     * Lightweight status snapshot intended for the in-app UI/debug screen.
     *
     * - settingKey/settingValue: what we *request* from the OS via secure settings
     * - displayHz: what the display is currently reporting
     */
    data class Status(
        val vendor: DeviceVendor,
        val settingKey: String,
        val settingValue: String,
        val displayHz: Float
    )

    /**
     * Reads the currently applied vendor setting (best-effort) and the current display refresh rate.
     */
    fun readStatus(context: Context): Status {
        val vendor = DeviceVendorDetector.detect()
        val cr = context.contentResolver

        // NOTE: Reading secure keys can be restricted on some ROMs; keep UI stable.
        val (key, valueStr) = try {
            when (vendor) {
                DeviceVendor.XIAOMI -> {
                    val v = Settings.Secure.getInt(cr, KEY_XIAOMI_REFRESH_RATE)
                    KEY_XIAOMI_REFRESH_RATE to v.toString()
                }
                DeviceVendor.SAMSUNG -> {
                    val v = Settings.Secure.getInt(cr, KEY_REFRESH_MODE)
                    val label = when (v) {
                        0 -> "0 (Normal/Min)"
                        1 -> "1 (Adaptive)"
                        2 -> "2 (High/Max)"
                        else -> v.toString()
                    }
                    KEY_REFRESH_MODE to label
                }
                DeviceVendor.OTHER -> {
                    // Best-effort fallback
                    val v = Settings.Secure.getInt(cr, KEY_REFRESH_MODE)
                    KEY_REFRESH_MODE to v.toString()
                }
            }
        } catch (_: Exception) {
            "(unavailable)" to "(unavailable)"
        }

        val displayHz = try {
            context.display.refreshRate
        } catch (_: Exception) {
            0f
        }

        return Status(
            vendor = vendor,
            settingKey = key,
            settingValue = valueStr,
            displayHz = displayHz
        )
    }
}