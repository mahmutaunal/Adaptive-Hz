package com.mahmutalperenunal.adaptivehz.core.system

import android.content.ContentValues
import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt

/**
 * Centralized helper responsible for reading and writing refresh-rate related
 * system settings in a vendor-aware manner.
 */
object RefreshRateController {

    const val KEY_REFRESH_MODE = "refresh_rate_mode"        // Samsung
    const val KEY_XIAOMI_REFRESH_RATE = "miui_refresh_rate" // Xiaomi/HyperOS

    /** Forces the device into its lowest supported refresh rate (vendor-specific). */
    fun applyForceMinimum(context: Context) {
        when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                val (minHz, _) = resolveDisplayMinMax(context)
                writeAny(context, KEY_XIAOMI_REFRESH_RATE, minHz)
            }
            DeviceVendor.SAMSUNG -> {
                writeSecure(context, KEY_REFRESH_MODE, 0)
            }
            DeviceVendor.OTHER -> {
                // best-effort: do nothing (stability > guessing)
            }
        }
    }

    /** Forces the device into its highest supported refresh rate (vendor-specific). */
    fun applyForceMaximum(context: Context) {
        when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                val (_, maxHz) = resolveDisplayMinMax(context)
                writeAny(context, KEY_XIAOMI_REFRESH_RATE, maxHz)
            }
            DeviceVendor.SAMSUNG -> {
                writeSecure(context, KEY_REFRESH_MODE, 2)
            }
            DeviceVendor.OTHER -> {
                // do nothing
            }
        }
    }

    /**
     * Restores the device to the vendor's default / adaptive refresh rate mode.
     * Used when the user globally disables the app.
     */
    fun resetToSystemDefault(context: Context) {
        when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                // Xiaomi/HyperOS typically treats the highest available rate as the default adaptive state
                val (_, maxHz) = resolveDisplayMinMax(context)
                writeAny(context, KEY_XIAOMI_REFRESH_RATE, maxHz)
            }
            DeviceVendor.SAMSUNG -> {
                // Samsung adaptive mode
                writeSecure(context, KEY_REFRESH_MODE, 1)
            }
            DeviceVendor.OTHER -> {
                // Unsupported vendors: do nothing for safety
            }
        }
    }

    private fun writeAny(context: Context, key: String, value: Int): Boolean {
        // Try Secure first, then fall back to System/Global for OEM-specific behavior.
        return writeSecure(context, key, value)
                || writeSystem(context, key, value)
                || writeGlobal(context, key, value)
    }

    private fun writeSecure(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.Secure.putInt(context.contentResolver, key, value)
            true
        } catch (t: Throwable) {
            Log.e(ContentValues.TAG, "Secure write failed: $key=$value", t)
            false
        }
    }

    private fun writeSystem(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.System.putInt(context.contentResolver, key, value)
            true
        } catch (t: Throwable) {
            Log.e(ContentValues.TAG, "System write failed: $key=$value", t)
            false
        }
    }

    private fun writeGlobal(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, key, value)
            true
        } catch (t: Throwable) {
            Log.e(ContentValues.TAG, "Global write failed: $key=$value", t)
            false
        }
    }

    /**
     * Unified write entry point used by the engine.
     * Delegates to the safest available settings namespace per vendor.
     */
    fun writeSetting(context: Context, key: String, value: Int): Boolean {
        return when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> writeAny(context, key, value)
            DeviceVendor.SAMSUNG -> writeSecure(context, key, value)
            DeviceVendor.OTHER -> writeSecure(context, key, value)
        }
    }

    /**
     * Resolves the device-supported min and max refresh rates
     * using Display.supportedModes as a vendor-agnostic source of truth.
     */
    fun resolveDisplayMinMax(context: Context): Pair<Int, Int> {
        val rates = try {
            context.display.supportedModes
                .map { it.refreshRate.roundToInt() }
                .distinct()
                .sorted()
        } catch (_: Throwable) {
            emptyList()
        }

        // Fallback to 60/120 if supportedModes is unavailable or restricted.
        val rawMin = rates.firstOrNull() ?: 60
        val rawMax = rates.lastOrNull() ?: 120
        val minHz = if (rawMin < 60) 60 else rawMin
        val maxHz = if (rawMax < minHz) minHz else rawMax
        return minHz to maxHz
    }

    data class Status(
        val vendor: DeviceVendor,
        val settingKey: String,
        val settingValue: String,
        val displayHz: Float
    )

    /**
     * Reads current vendor setting (best-effort) and the actual display refresh rate
     * for debug/UI purposes.
     */
    fun readStatus(context: Context): Status {
        val vendor = DeviceVendorDetector.detect()
        val cr = context.contentResolver

        val (key, valueStr) = try {
            when (vendor) {
                DeviceVendor.XIAOMI -> {
                    val v = runCatching { Settings.Secure.getInt(cr, KEY_XIAOMI_REFRESH_RATE) }.getOrNull()
                        ?: runCatching { Settings.System.getInt(cr, KEY_XIAOMI_REFRESH_RATE) }.getOrNull()
                        ?: runCatching { Settings.Global.getInt(cr, KEY_XIAOMI_REFRESH_RATE) }.getOrNull()
                    KEY_XIAOMI_REFRESH_RATE to (v?.toString() ?: "(unavailable)")
                }
                DeviceVendor.SAMSUNG -> {
                    val label = when (val v = Settings.Secure.getInt(cr, KEY_REFRESH_MODE)) {
                        0 -> "0 (Normal/Min)"
                        1 -> "1 (Adaptive)"
                        2 -> "2 (High/Max)"
                        else -> v.toString()
                    }
                    KEY_REFRESH_MODE to label
                }
                DeviceVendor.OTHER -> {
                    "(unsupported)" to "(unsupported)"
                }
            }
        } catch (_: Throwable) {
            "(unavailable)" to "(unavailable)"
        }

        val hz = try { context.display.refreshRate } catch (_: Throwable) { 0f }
        return Status(vendor, key, valueStr, hz)
    }

    fun isBatterySaverOn(context: Context): Boolean {
        return try {
            val powerManager =
                context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isPowerSaveMode
        } catch (_: Throwable) {
            false
        }
    }
}