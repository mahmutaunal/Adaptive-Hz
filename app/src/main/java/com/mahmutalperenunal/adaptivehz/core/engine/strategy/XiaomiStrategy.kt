package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController

/**
 * Refresh-rate strategy for Xiaomi, Redmi, and Poco devices running MIUI or HyperOS.
 *
 * Based on current community testing:
 *
 * HyperOS 1:
 * - Uses secure/user_refresh_rate.
 * - Adaptive LOW/HIGH modes use physical refresh-rate values.
 * - Persistent FORCE_MAX mode uses the special value 1.
 *
 * HyperOS 2:
 * - Uses secure/miui_refresh_rate.
 * - Uses physical refresh-rate values.
 *
 * HyperOS 3:
 * - Uses secure/miui_refresh_rate by default.
 * - Device- and ROM-specific differences are observed through Diagnostics.
 */
class XiaomiStrategy : VendorStrategy {

    override val name: String = "Xiaomi/HyperOS"

    // Builds the setting write that requests the lowest supported refresh rate.
    override fun desiredLow(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (minHz, _) = RefreshRateController.resolveDisplayMinMax(appContext)
        val profile = RefreshRateController.resolveXiaomiProfile()

        return SettingWrite(
            namespace = profile.namespace,
            key = profile.settingKey,
            intValue = minHz,
            label = "${profile.path}=$minHz (Minimum, ${profile.label})"
        )
    }

    // Builds the setting write that requests the highest adaptive refresh rate.
    override fun desiredHigh(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (_, maxHz) = RefreshRateController.resolveDisplayMinMax(appContext)
        val profile = RefreshRateController.resolveXiaomiProfile()

        return SettingWrite(
            namespace = profile.namespace,
            key = profile.settingKey,
            intValue = maxHz,
            label = "${profile.path}=$maxHz (Adaptive High, ${profile.label})"
        )
    }

    // Builds the vendor-specific setting write for persistent maximum refresh rate.
    override fun desiredForceMaximum(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (_, maxHz) = RefreshRateController.resolveDisplayMinMax(appContext)
        val profile = RefreshRateController.resolveXiaomiProfile()

        // Some Xiaomi profiles require a special value instead of the physical maximum Hz.
        val value = profile.resolveForceMaximumValue(maxHz)

        return SettingWrite(
            namespace = profile.namespace,
            key = profile.settingKey,
            intValue = value,
            label = "${profile.path}=$value (Force Maximum, ${profile.label})"
        )
    }

    // Builds the setting write that returns refresh-rate control to the system.
    override fun desiredSystemControlled(context: Context): SettingWrite {
        val profile = RefreshRateController.resolveXiaomiProfile()

        return SettingWrite(
            namespace = profile.namespace,
            key = profile.settingKey,
            intValue = 0,
            label = "${profile.path}=0 (System Controlled, ${profile.label})"
        )
    }

    // Provides Xiaomi-specific interaction and event tuning for the adaptive engine.
    override fun tuning(): VendorTuning {
        return VendorTuning(
            interactionIdleTimeoutMs = 2_000L,
            eventCoalescingWindowMs = 60L,
            allowContentChangeBoost = true,
            allowScrollBoost = true,
            extendBoostOnWindowChange = true
        )
    }
}