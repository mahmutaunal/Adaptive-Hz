package com.mahmutalperenunal.adaptivehz.core.system

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import java.util.Locale
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

    /**
     * Xiaomi/HyperOS refresh-rate compatibility profile.
     */
    data class XiaomiRefreshProfile(
        val hyperOsMajor: Int?,
        val settingKey: String,
        val forceMaximumUsesSpecialValue: Boolean,
        val label: String
    ) {
        fun resolveForceMaximumValue(maxHz: Int): Int {
            return if (forceMaximumUsesSpecialValue) 1 else maxHz
        }
    }

    /** Forces the device into its lowest supported refresh rate (vendor-specific). */
    fun applyForceMinimum(context: Context) {
        val appContext = context.applicationContext

        when (DeviceVendorDetector.detect()) {
            DeviceVendor.XIAOMI -> {
                val (minHz, _) = resolveDisplayMinMax(appContext)
                val profile = resolveXiaomiProfile()

                writeXiaomiRefreshRate(
                    context = appContext,
                    profile = profile,
                    value = minHz
                )
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
                val profile = resolveXiaomiProfile()
                val value = profile.resolveForceMaximumValue(maxHz)

                writeXiaomiRefreshRate(
                    context = appContext,
                    profile = profile,
                    value = value
                )
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
                val profile = resolveXiaomiProfile()

                writeXiaomiRefreshRate(
                    context = appContext,
                    profile = profile,
                    value = 0
                )
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
     * Resolves the Xiaomi refresh-rate implementation used by the current ROM.
     *
     * Current compatibility policy:
     *
     * HyperOS 1 -> secure/user_refresh_rate
     * HyperOS 2 -> secure/miui_refresh_rate
     * HyperOS 3 -> secure/miui_refresh_rate
     *
     * Unknown Xiaomi/MIUI builds fall back to miui_refresh_rate
     * because this was the project's original stable behavior.
     */
    fun resolveXiaomiProfile(): XiaomiRefreshProfile {
        val hyperOsMajor = detectHyperOsMajorVersion()

        return when (hyperOsMajor) {
            1 -> XiaomiRefreshProfile(
                hyperOsMajor = 1,
                settingKey = KEY_XIAOMI_USER_REFRESH_RATE,
                forceMaximumUsesSpecialValue = true,
                label = "HyperOS 1"
            )

            2 -> XiaomiRefreshProfile(
                hyperOsMajor = 2,
                settingKey = KEY_XIAOMI_REFRESH_RATE,
                forceMaximumUsesSpecialValue = false,
                label = "HyperOS 2"
            )

            3 -> XiaomiRefreshProfile(
                hyperOsMajor = 3,
                settingKey = KEY_XIAOMI_REFRESH_RATE,
                forceMaximumUsesSpecialValue = false,
                label = "HyperOS 3"
            )

            else -> XiaomiRefreshProfile(
                hyperOsMajor = hyperOsMajor,
                settingKey = KEY_XIAOMI_REFRESH_RATE,
                forceMaximumUsesSpecialValue = false,
                label = if (hyperOsMajor != null) {
                    "HyperOS $hyperOsMajor fallback"
                } else {
                    "MIUI/Unknown Xiaomi fallback"
                }
            )
        }
    }

    /**
     * Best-effort HyperOS major-version detection.
     *
     * Xiaomi commonly exposes values such as:
     * - OS1.0
     * - OS2.0
     * - OS3.0
     *
     * Reflection may fail on some Android builds, so Build fields
     * are used as a fallback.
     */
    private fun detectHyperOsMajorVersion(): Int? {
        val candidates = buildList {
            readSystemProperty("ro.mi.os.version.name")
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)

            readSystemProperty("ro.mi.os.version.incremental")
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)

            readSystemProperty("ro.build.version.incremental")
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)

            Build.VERSION.INCREMENTAL
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)

            Build.DISPLAY
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }

        for (candidate in candidates) {
            parseHyperOsMajor(candidate)?.let { return it }
        }

        return null
    }

    private fun parseHyperOsMajor(rawValue: String): Int? {
        val normalized = rawValue
            .trim()
            .uppercase(Locale.ROOT)

        val patterns = listOf(
            Regex("""(?:HYPER\s*OS|HYPEROS)\s*[_\- ]?(\d+)"""),
            Regex("""(?:^|[^A-Z])OS\s*[_\- ]?(\d+)(?:\.|[^0-9]|$)""")
        )

        for (pattern in patterns) {
            val result = pattern.find(normalized) ?: continue
            val major = result.groupValues.getOrNull(1)?.toIntOrNull()

            if (major != null) {
                return major
            }
        }

        return null
    }

    @SuppressLint("PrivateApi")
    private fun readSystemProperty(key: String): String? {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod(
                "get",
                String::class.java,
                String::class.java
            )

            getMethod.invoke(null, key, "") as? String
        } catch (t: Throwable) {
            Log.d(TAG, "Unable to read system property: $key", t)
            null
        }
    }

    /**
     * Writes only the refresh-rate key selected for the detected HyperOS version.
     *
     * Dual-writing user_refresh_rate and miui_refresh_rate is intentionally avoided,
     * because Xiaomi ROM versions may assign different policy semantics to them.
     */
    private fun writeXiaomiRefreshRate(
        context: Context,
        profile: XiaomiRefreshProfile,
        value: Int
    ): Boolean {
        val success = writeSecure(
            context = context,
            key = profile.settingKey,
            value = value
        )

        Log.d(
            TAG,
            "Xiaomi refresh write " +
                    "profile=${profile.label}, " +
                    "hyperOsMajor=${profile.hyperOsMajor}, " +
                    "key=${profile.settingKey}, " +
                    "value=$value, " +
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
                    KEY_XIAOMI_REFRESH_RATE,
                    KEY_XIAOMI_USER_REFRESH_RATE -> {
                        val profile = resolveXiaomiProfile()

                        // Use the strategy-selected value, but always write only
                        // the key belonging to the detected HyperOS profile.
                        writeXiaomiRefreshRate(
                            context = appContext,
                            profile = profile,
                            value = value
                        )
                    }

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
                    val profile = resolveXiaomiProfile()

                    val activeValue = runCatching {
                        Settings.Secure.getInt(cr, profile.settingKey)
                    }.getOrNull()

                    val label = buildString {
                        append("profile=").append(profile.label)
                        append(", key=").append(profile.settingKey)
                        append(", value=")
                        append(activeValue?.toString() ?: "(unavailable)")
                    }

                    profile.settingKey to label
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