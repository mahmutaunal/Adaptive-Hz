package com.mahmutalperenunal.adaptivehz.core.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import com.mahmutalperenunal.adaptivehz.core.engine.model.RefreshRateApplyResult
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingsNamespace
import java.util.Locale
import kotlin.math.roundToInt

// Centralizes vendor-specific refresh-rate writes, detection, verification, and diagnostics.
object RefreshRateController {

    private const val TAG = "RefreshRateController"

    const val KEY_REFRESH_MODE = "refresh_rate_mode"
    const val KEY_XIAOMI_REFRESH_RATE = "miui_refresh_rate"
    const val KEY_XIAOMI_USER_REFRESH_RATE = "user_refresh_rate"
    const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"
    const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"

    // Defines whether generic refresh-rate limits should also be applied.
    enum class RefreshWritePolicy {
        NORMAL,
        BATTERY_SAVER_OVERRIDE_HIGH,
        BATTERY_SAVER_OVERRIDE_LOW
    }

    // Indicates how reliable the detected HyperOS version source is.
    enum class DetectionConfidence {
        HIGH,
        MEDIUM,
        FALLBACK
    }

    // Captures the detected HyperOS version and the source used to resolve it.
    data class HyperOsDetection(
        val majorVersion: Int?,
        val sourceProperty: String?,
        val rawValue: String?,
        val confidence: DetectionConfidence
    )

    // Describes the settings path and value behavior for a Xiaomi software profile.
    data class XiaomiRefreshProfile(
        val detection: HyperOsDetection,
        val namespace: SettingsNamespace,
        val settingKey: String,
        val forceMaximumUsesSpecialValue: Boolean,
        val label: String
    ) {
        // Fully qualified settings path used in logs and diagnostics.
        val path: String
            get() = "${namespace.name.lowercase(Locale.ROOT)}/$settingKey"

        // Resolves the physical or vendor-specific value used for maximum refresh rate.
        fun resolveForceMaximumValue(maxHz: Int): Int {
            return if (forceMaximumUsesSpecialValue) 1 else maxHz
        }
    }

    // Represents a diagnostic snapshot of a candidate Android setting.
    data class SettingSnapshot(
        val namespace: SettingsNamespace,
        val key: String,
        val value: Int?,
        val readable: Boolean
    )

    // Aggregates the current vendor, selected settings path, and display diagnostics.
    data class Status(
        val vendor: DeviceVendor,
        val selectedWritePath: String,
        val selectedValue: String,
        val displayHz: Float,
        val hyperOsDetection: HyperOsDetection?,
        val candidateSettings: List<SettingSnapshot>
    )

    // Applies a settings write, optionally updates generic limits, and verifies the result.
    fun applySetting(
        context: Context,
        write: SettingWrite,
        policy: RefreshWritePolicy = RefreshWritePolicy.NORMAL,
        genericRefreshRateHz: Int = write.intValue
    ): RefreshRateApplyResult {
        val appContext = context.applicationContext

        val writeSucceeded = try {
            writeInt(
                context = appContext,
                namespace = write.namespace,
                key = write.key,
                value = write.intValue
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Write failed with exception: ${write.label}", t)

            return RefreshRateApplyResult.Failure(
                requestedWrite = write,
                throwable = t
            )
        }

        if (!writeSucceeded) {
            Log.w(TAG, "Settings provider rejected write: ${write.label}")

            return RefreshRateApplyResult.WriteRejected(
                requestedWrite = write
            )
        }

        // Battery Saver overrides may require generic min and peak limits as a fallback.
        val genericApplied = when (policy) {
            RefreshWritePolicy.NORMAL -> false

            RefreshWritePolicy.BATTERY_SAVER_OVERRIDE_HIGH,
            RefreshWritePolicy.BATTERY_SAVER_OVERRIDE_LOW -> {
                writeGenericRefreshRateLimits(
                    context = appContext,
                    refreshRateHz = genericRefreshRateHz
                )
            }
        }

        // Read the value back when possible to verify that the provider accepted it.
        val readBack = readInt(
            context = appContext,
            namespace = write.namespace,
            key = write.key
        )

        val result = when (readBack) {
            write.intValue -> {
                RefreshRateApplyResult.AppliedAndVerified(
                    requestedWrite = write,
                    readBackValue = readBack,
                    genericOverrideApplied = genericApplied
                )
            }
            null -> {
                RefreshRateApplyResult.WrittenButUnverified(
                    requestedWrite = write,
                    genericOverrideApplied = genericApplied
                )
            }
            else -> {
                RefreshRateApplyResult.VerificationMismatch(
                    requestedWrite = write,
                    readBackValue = readBack,
                    genericOverrideApplied = genericApplied
                )
            }
        }

        Log.d(
            TAG,
            "applySetting " +
                    "path=${write.namespace}/${write.key}, " +
                    "requested=${write.intValue}, " +
                    "readBack=$readBack, " +
                    "policy=$policy, " +
                    "genericApplied=$genericApplied, " +
                    "result=${result::class.simpleName}"
        )

        return result
    }

    // Resolves the Xiaomi settings profile that matches the detected HyperOS version.
    fun resolveXiaomiProfile(): XiaomiRefreshProfile {
        val detection = detectHyperOsVersion()

        return when (detection.majorVersion) {
            1 -> XiaomiRefreshProfile(
                detection = detection,
                namespace = SettingsNamespace.SECURE,
                settingKey = KEY_XIAOMI_USER_REFRESH_RATE,
                forceMaximumUsesSpecialValue = true,
                label = "HyperOS 1"
            )

            2 -> XiaomiRefreshProfile(
                detection = detection,
                namespace = SettingsNamespace.SECURE,
                settingKey = KEY_XIAOMI_REFRESH_RATE,
                forceMaximumUsesSpecialValue = false,
                label = "HyperOS 2"
            )

            3 -> XiaomiRefreshProfile(
                detection = detection,
                namespace = SettingsNamespace.SECURE,
                settingKey = KEY_XIAOMI_REFRESH_RATE,
                forceMaximumUsesSpecialValue = false,
                label = "HyperOS 3"
            )

            else -> XiaomiRefreshProfile(
                detection = detection,
                namespace = SettingsNamespace.SECURE,
                settingKey = KEY_XIAOMI_REFRESH_RATE,
                forceMaximumUsesSpecialValue = false,
                label = detection.majorVersion?.let {
                    "HyperOS $it fallback"
                } ?: "MIUI/Unknown Xiaomi fallback"
            )
        }
    }

    // Detects the HyperOS major version from vendor and build properties.
    fun detectHyperOsVersion(): HyperOsDetection {
        val candidates = listOf(
            "ro.mi.os.version.name" to readSystemProperty("ro.mi.os.version.name"),
            "ro.mi.os.version.incremental" to
                    readSystemProperty("ro.mi.os.version.incremental"),
            "ro.build.version.incremental" to
                    readSystemProperty("ro.build.version.incremental"),
            "Build.VERSION.INCREMENTAL" to Build.VERSION.INCREMENTAL,
            "Build.DISPLAY" to Build.DISPLAY
        )

        candidates.forEachIndexed { index, (source, rawValue) ->
            val raw = rawValue?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val major = parseHyperOsMajor(raw) ?: return@forEachIndexed

            return HyperOsDetection(
                majorVersion = major,
                sourceProperty = source,
                rawValue = raw,
                confidence = when (index) {
                    0, 1 -> DetectionConfidence.HIGH
                    2 -> DetectionConfidence.MEDIUM
                    else -> DetectionConfidence.FALLBACK
                }
            )
        }

        return HyperOsDetection(
            majorVersion = null,
            sourceProperty = null,
            rawValue = null,
            confidence = DetectionConfidence.FALLBACK
        )
    }

    // Extracts the HyperOS major version from known property formats.
    private fun parseHyperOsMajor(rawValue: String): Int? {
        val normalized = rawValue.trim().uppercase(Locale.ROOT)

        val patterns = listOf(
            Regex("""(?:HYPER\s*OS|HYPEROS)\s*[_\- ]?(\d+)"""),
            Regex("""(?:^|[^A-Z])OS\s*[_\- ]?(\d+)(?:\.|[^0-9]|$)""")
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
    }

    // Reads hidden Android system properties through reflection when available.
    @SuppressLint("PrivateApi")
    private fun readSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod(
                "get",
                String::class.java,
                String::class.java
            )

            method.invoke(null, key, "") as? String
        } catch (t: Throwable) {
            Log.d(TAG, "Unable to read system property: $key", t)
            null
        }
    }

    // Writes an integer value to the requested Android settings namespace.
    private fun writeInt(
        context: Context,
        namespace: SettingsNamespace,
        key: String,
        value: Int
    ): Boolean {
        return when (namespace) {
            SettingsNamespace.SECURE -> {
                Settings.Secure.putInt(context.contentResolver, key, value)
            }

            SettingsNamespace.SYSTEM -> {
                Settings.System.putInt(context.contentResolver, key, value)
            }

            SettingsNamespace.GLOBAL -> {
                Settings.Global.putInt(context.contentResolver, key, value)
            }
        }
    }

    // Reads an integer setting and returns null when the value is unavailable.
    fun readInt(
        context: Context,
        namespace: SettingsNamespace,
        key: String
    ): Int? {
        val resolver = context.applicationContext.contentResolver

        return runCatching {
            when (namespace) {
                SettingsNamespace.SECURE -> {
                    Settings.Secure.getInt(resolver, key)
                }

                SettingsNamespace.SYSTEM -> {
                    Settings.System.getInt(resolver, key)
                }

                SettingsNamespace.GLOBAL -> {
                    Settings.Global.getInt(resolver, key)
                }
            }
        }.getOrNull()
    }

    // Reads known Xiaomi refresh-rate keys across all settings namespaces for diagnostics.
    fun readXiaomiCandidateSettings(context: Context): List<SettingSnapshot> {
        val appContext = context.applicationContext

        val keys = listOf(
            KEY_XIAOMI_REFRESH_RATE,
            KEY_XIAOMI_USER_REFRESH_RATE
        )

        return SettingsNamespace.entries.flatMap { namespace ->
            keys.map { key ->
                val value = readInt(
                    context = appContext,
                    namespace = namespace,
                    key = key
                )

                SettingSnapshot(
                    namespace = namespace,
                    key = key,
                    value = value,
                    readable = value != null
                )
            }
        }
    }

    // Applies generic peak and minimum refresh-rate limits as a vendor-independent fallback.
    private fun writeGenericRefreshRateLimits(
        context: Context,
        refreshRateHz: Int
    ): Boolean {
        val value = refreshRateHz.toFloat()

        val peakSuccess = writeFloatWithFallback(
            context = context,
            key = KEY_PEAK_REFRESH_RATE,
            value = value
        )

        val minSuccess = writeFloatWithFallback(
            context = context,
            key = KEY_MIN_REFRESH_RATE,
            value = value
        )

        val success = peakSuccess || minSuccess

        Log.d(
            TAG,
            "Generic refresh limits: " +
                    "hz=$refreshRateHz, " +
                    "peak=$peakSuccess, min=$minSuccess, success=$success"
        )

        return success
    }

    // Attempts the same float write across supported settings namespaces.
    private fun writeFloatWithFallback(
        context: Context,
        key: String,
        value: Float
    ): Boolean {
        return writeFloat(
            context,
            SettingsNamespace.SYSTEM,
            key,
            value
        ) || writeFloat(
            context,
            SettingsNamespace.SECURE,
            key,
            value
        ) || writeFloat(
            context,
            SettingsNamespace.GLOBAL,
            key,
            value
        )
    }

    // Writes a float value to a specific Android settings namespace.
    private fun writeFloat(
        context: Context,
        namespace: SettingsNamespace,
        key: String,
        value: Float
    ): Boolean {
        return try {
            when (namespace) {
                SettingsNamespace.SECURE -> {
                    Settings.Secure.putFloat(
                        context.contentResolver,
                        key,
                        value
                    )
                }

                SettingsNamespace.SYSTEM -> {
                    Settings.System.putFloat(
                        context.contentResolver,
                        key,
                        value
                    )
                }

                SettingsNamespace.GLOBAL -> {
                    Settings.Global.putFloat(
                        context.contentResolver,
                        key,
                        value
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "Float write failed: namespace=$namespace key=$key value=$value",
                t
            )
            false
        }
    }

    // Resolves the minimum and maximum refresh rates reported by the active display.
    fun resolveDisplayMinMax(context: Context): Pair<Int, Int> {
        val appContext = context.applicationContext

        val rates = try {
            appContext.display.supportedModes
                .map { it.refreshRate.roundToInt() }
                .filter { it > 0 }
                .distinct()
                .sorted()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read supported display modes", t)
            emptyList()
        }

        val rawMin = rates.firstOrNull() ?: 60
        val rawMax = rates.lastOrNull() ?: 120

        val minHz = rawMin.coerceAtLeast(60)
        val maxHz = rawMax.coerceAtLeast(minHz)

        return minHz to maxHz
    }

    // Builds a vendor-aware diagnostic snapshot of the current refresh-rate state.
    fun readStatus(context: Context): Status {
        val appContext = context.applicationContext
        val vendor = DeviceVendorDetector.detect()

        val displayHz = runCatching {
            appContext.display.refreshRate
        }.getOrDefault(0f)

        return when (vendor) {
            DeviceVendor.XIAOMI -> {
                val profile = resolveXiaomiProfile()

                val selectedValue = readInt(
                    context = appContext,
                    namespace = profile.namespace,
                    key = profile.settingKey
                )

                Status(
                    vendor = vendor,
                    selectedWritePath = profile.path,
                    selectedValue = selectedValue?.toString() ?: "(unavailable)",
                    displayHz = displayHz,
                    hyperOsDetection = profile.detection,
                    candidateSettings = readXiaomiCandidateSettings(appContext)
                )
            }

            DeviceVendor.SAMSUNG -> {
                val value = readInt(
                    context = appContext,
                    namespace = SettingsNamespace.SECURE,
                    key = KEY_REFRESH_MODE
                )

                val label = when (value) {
                    0 -> "0 (Minimum/Normal)"
                    1 -> "1 (System/Adaptive)"
                    2 -> "2 (Maximum/High)"
                    null -> "(unavailable)"
                    else -> value.toString()
                }

                Status(
                    vendor = vendor,
                    selectedWritePath = "secure/$KEY_REFRESH_MODE",
                    selectedValue = label,
                    displayHz = displayHz,
                    hyperOsDetection = null,
                    candidateSettings = emptyList()
                )
            }

            DeviceVendor.OTHER -> {
                Status(
                    vendor = vendor,
                    selectedWritePath = "(experimental)",
                    selectedValue = "(unknown)",
                    displayHz = displayHz,
                    hyperOsDetection = null,
                    candidateSettings = emptyList()
                )
            }
        }
    }

    // Returns whether Android Battery Saver is currently enabled.
    fun isBatterySaverOn(context: Context): Boolean {
        return try {
            val powerManager = context.applicationContext
                .getSystemService(Context.POWER_SERVICE) as PowerManager

            powerManager.isPowerSaveMode
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read Battery Saver state", t)
            false
        }
    }
}