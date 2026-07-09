package com.mahmutalperenunal.adaptivehz.core.system

import android.content.ContentValues
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import kotlin.math.roundToInt

/**
 * Centralized helper responsible for reading and writing refresh-rate related
 * system settings in a vendor-aware manner.
 */
object RefreshRateController {

    private const val TAG = "RefreshRateController"

    const val KEY_REFRESH_MODE = "refresh_rate_mode" // Samsung

    /**
     * Legacy MIUI / Xiaomi refresh-rate key.
     *
     * Some MIUI and older HyperOS builds still react to this setting.
     */
    const val KEY_XIAOMI_REFRESH_RATE = "miui_refresh_rate"

    /**
     * HyperOS refresh-rate key.
     *
     * Some HyperOS 1 builds ignore miui_refresh_rate and require this secure setting instead.
     */
    const val KEY_XIAOMI_USER_REFRESH_RATE = "user_refresh_rate"

    const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"
    const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"

    enum class RefreshWritePolicy {
        NORMAL,
        BATTERY_SAVER_OVERRIDE_HIGH,
        BATTERY_SAVER_OVERRIDE_LOW
    }

    /** Forces the device into its lowest supported refresh rate (vendor-specific). */
    fun applyForceMinimum(context: Context) {
        val appContext = context.applicationContext

        when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                val (minHz, _) = resolveDisplayMinMax(appContext)
                writeXiaomiRefreshRate(appContext, minHz)
            }

            DeviceVendor.SAMSUNG -> {
                writeSecure(appContext, KEY_REFRESH_MODE, 0)
            }

            DeviceVendor.OTHER -> {
                // Best-effort: do nothing (stability > guessing).
            }
        }
    }

    /** Forces the device into its highest supported refresh rate (vendor-specific). */
    fun applyForceMaximum(context: Context) {
        val appContext = context.applicationContext

        when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                val (_, maxHz) = resolveDisplayMinMax(appContext)
                writeXiaomiRefreshRate(appContext, maxHz)
            }

            DeviceVendor.SAMSUNG -> {
                writeSecure(appContext, KEY_REFRESH_MODE, 2)
            }

            DeviceVendor.OTHER -> {
                // Unsupported vendors: do nothing for safety.
            }
        }
    }

    /**
     * Restores the device to the vendor's default / adaptive refresh rate mode.
     * Used when the user globally disables the app.
     */
    fun resetToSystemDefault(context: Context) {
        val appContext = context.applicationContext

        when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                // Xiaomi/HyperOS behavior differs by build.
                // Writing 0 attempts to restore vendor/system-controlled behavior.
                writeXiaomiRefreshRate(appContext, 0)
            }

            DeviceVendor.SAMSUNG -> {
                // Samsung adaptive mode.
                writeSecure(appContext, KEY_REFRESH_MODE, 1)
            }

            DeviceVendor.OTHER -> {
                // Unsupported vendors: do nothing for safety.
            }
        }
    }

    /**
     * Writes Xiaomi / MIUI / HyperOS refresh-rate settings without breaking existing behavior.
     *
     * Important:
     * - HyperOS 1 may require secure/user_refresh_rate.
     * - MIUI / older HyperOS builds may still require miui_refresh_rate.
     * - We keep the existing miui_refresh_rate fallback path for backward compatibility.
     *
     * At least one successful write is treated as success.
     */
    private fun writeXiaomiRefreshRate(context: Context, value: Int): Boolean {
        val userRefreshRateResult = writeSecure(
            context = context,
            key = KEY_XIAOMI_USER_REFRESH_RATE,
            value = value
        )

        val miuiRefreshRateResult = writeAny(
            context = context,
            key = KEY_XIAOMI_REFRESH_RATE,
            value = value
        )

        val success = userRefreshRateResult || miuiRefreshRateResult

        Log.d(
            TAG,
            "Xiaomi refresh write value=$value, " +
                    "user_refresh_rate=$userRefreshRateResult, " +
                    "miui_refresh_rate=$miuiRefreshRateResult, " +
                    "success=$success"
        )

        return success
    }

    private fun writeAny(context: Context, key: String, value: Int): Boolean {
        // Try Secure first, then fall back to System/Global for OEM-specific behavior.
        return writeSecure(context, key, value) ||
                writeSystem(context, key, value) ||
                writeGlobal(context, key, value)
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

    private fun writeGenericRefreshRateLimits(
        context: Context,
        refreshRateHz: Int
    ): Boolean {
        val rate = refreshRateHz.toFloat()

        val peakResult = writeAnyFloat(
            context = context,
            key = KEY_PEAK_REFRESH_RATE,
            value = rate
        )

        val minResult = writeAnyFloat(
            context = context,
            key = KEY_MIN_REFRESH_RATE,
            value = rate
        )

        val success = peakResult || minResult

        Log.d(
            TAG,
            "Generic refresh limits write refreshRateHz=$refreshRateHz, " +
                    "peak_refresh_rate=$peakResult, " +
                    "min_refresh_rate=$minResult, " +
                    "success=$success"
        )

        return success
    }

    private fun writeAnyFloat(context: Context, key: String, value: Float): Boolean {
        return writeSystemFloat(context, key, value) ||
                writeSecureFloat(context, key, value) ||
                writeGlobalFloat(context, key, value)
    }

    private fun writeSystemFloat(context: Context, key: String, value: Float): Boolean {
        return try {
            Settings.System.putFloat(context.contentResolver, key, value)
            true
        } catch (t: Throwable) {
            Log.e(ContentValues.TAG, "System float write failed: $key=$value", t)
            false
        }
    }

    private fun writeSecureFloat(context: Context, key: String, value: Float): Boolean {
        return try {
            Settings.Secure.putFloat(context.contentResolver, key, value)
            true
        } catch (t: Throwable) {
            Log.e(ContentValues.TAG, "Secure float write failed: $key=$value", t)
            false
        }
    }

    private fun writeGlobalFloat(context: Context, key: String, value: Float): Boolean {
        return try {
            Settings.Global.putFloat(context.contentResolver, key, value)
            true
        } catch (t: Throwable) {
            Log.e(ContentValues.TAG, "Global float write failed: $key=$value", t)
            false
        }
    }

    /**
     * Unified write entry point used by the engine.
     * Delegates to the safest available settings namespace per vendor.
     */
    fun writeSetting(
        context: Context,
        key: String,
        value: Int,
        policy: RefreshWritePolicy = RefreshWritePolicy.NORMAL,
        genericRefreshRateHz: Int = value
    ): Boolean {
        val appContext = context.applicationContext

        val vendorResult = when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                when (key) {
                    KEY_XIAOMI_REFRESH_RATE, KEY_XIAOMI_USER_REFRESH_RATE -> writeXiaomiRefreshRate(appContext, value)
                    else -> writeAny(appContext, key, value)
                }
            }
            DeviceVendor.SAMSUNG -> writeSecure(appContext, key, value)
            DeviceVendor.OTHER -> writeSecure(appContext, key, value)
        }

        val genericResult = when (policy) {
            RefreshWritePolicy.NORMAL -> false

            RefreshWritePolicy.BATTERY_SAVER_OVERRIDE_HIGH,
            RefreshWritePolicy.BATTERY_SAVER_OVERRIDE_LOW -> {
                writeGenericRefreshRateLimits(
                    context = appContext,
                    refreshRateHz = genericRefreshRateHz
                )
            }
        }

        val success = vendorResult || genericResult

        Log.d(
            TAG,
            "writeSetting key=$key value=$value policy=$policy " +
                    "vendorResult=$vendorResult genericResult=$genericResult success=$success"
        )

        return success
    }

    /**
     * Resolves the device-supported min and max refresh rates
     * using Display.supportedModes as a vendor-agnostic source of truth.
     */
    fun resolveDisplayMinMax(context: Context): Pair<Int, Int> {
        val appContext = context.applicationContext

        val rates = try {
            appContext.display.supportedModes
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
        val appContext = context.applicationContext
        val vendor = DeviceVendorDetector.detect()
        val cr = appContext.contentResolver

        val (key, valueStr) = try {
            when (vendor) {
                DeviceVendor.XIAOMI -> {
                    val userRefreshRate = runCatching {
                        Settings.Secure.getInt(cr, KEY_XIAOMI_USER_REFRESH_RATE)
                    }.getOrNull()

                    val miuiRefreshRate = runCatching {
                        Settings.Secure.getInt(cr, KEY_XIAOMI_REFRESH_RATE)
                    }.getOrNull()
                        ?: runCatching {
                            Settings.System.getInt(cr, KEY_XIAOMI_REFRESH_RATE)
                        }.getOrNull()
                        ?: runCatching {
                            Settings.Global.getInt(cr, KEY_XIAOMI_REFRESH_RATE)
                        }.getOrNull()

                    val label = buildString {
                        append("$KEY_XIAOMI_USER_REFRESH_RATE=")
                        append(userRefreshRate?.toString() ?: "(unavailable)")
                        append(", ")
                        append("$KEY_XIAOMI_REFRESH_RATE=")
                        append(miuiRefreshRate?.toString() ?: "(unavailable)")
                    }

                    "Xiaomi/HyperOS" to label
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

        val hz = try {
            appContext.display.refreshRate
        } catch (_: Throwable) {
            0f
        }

        return Status(vendor, key, valueStr, hz)
    }

    fun isBatterySaverOn(context: Context): Boolean {
        val appContext = context.applicationContext

        return try {
            val powerManager =
                appContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isPowerSaveMode
        } catch (_: Throwable) {
            false
        }
    }
}