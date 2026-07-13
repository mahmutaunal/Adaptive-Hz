package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController

/**
 * Xiaomi / MIUI / HyperOS refresh-rate strategy.
 *
 * Confirmed compatibility behavior:
 *
 * HyperOS 1:
 * - Uses secure/user_refresh_rate
 * - Adaptive LOW/HIGH uses explicit Hz values
 * - Persistent FORCE_MAX uses the special value 1
 *
 * HyperOS 2/3:
 * - Uses secure/miui_refresh_rate
 * - Uses explicit Hz values for LOW/HIGH/FORCE_MAX
 */
class XiaomiStrategy : VendorStrategy {

    override val name: String = "Xiaomi"

    /**
     * Adaptive LOW and FORCE_MIN both use the physical minimum Hz.
     */
    override fun desiredLow(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (minHz, _) = RefreshRateController.resolveDisplayMinMax(appContext)
        val profile = RefreshRateController.resolveXiaomiProfile()

        return SettingWrite(
            key = profile.settingKey,
            intValue = minHz,
            label = "${profile.settingKey}=$minHz (Min, ${profile.label})"
        )
    }

    /**
     * Temporary HIGH used by Adaptive mode.
     *
     * Important: HyperOS 1 still receives the actual maximum Hz here.
     * The special value 1 is used only for persistent FORCE_MAX.
     */
    override fun desiredHigh(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (_, maxHz) = RefreshRateController.resolveDisplayMinMax(appContext)
        val profile = RefreshRateController.resolveXiaomiProfile()

        return SettingWrite(
            key = profile.settingKey,
            intValue = maxHz,
            label = "${profile.settingKey}=$maxHz (Adaptive High, ${profile.label})"
        )
    }

    /**
     * Persistent maximum mode.
     *
     * HyperOS 1 requires user_refresh_rate=1 to force maximum
     * refresh rate across all apps.
     *
     * HyperOS 2/3 continue to use the explicit physical maximum Hz.
     */
    override fun desiredForceMaximum(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (_, maxHz) = RefreshRateController.resolveDisplayMinMax(appContext)
        val profile = RefreshRateController.resolveXiaomiProfile()

        val value = profile.resolveForceMaximumValue(maxHz)

        return SettingWrite(
            key = profile.settingKey,
            intValue = value,
            label = "${profile.settingKey}=$value (Force Max, ${profile.label})"
        )
    }

    override fun desiredSystemControlled(context: Context): SettingWrite {
        val profile = RefreshRateController.resolveXiaomiProfile()

        return SettingWrite(
            key = profile.settingKey,
            intValue = 0,
            label = "${profile.settingKey}=0 (System, ${profile.label})"
        )
    }

    override fun tuning(): VendorTuning {
        return VendorTuning(
            interactionIdleTimeoutMs = 2000L,
            eventCoalescingWindowMs = 60L,
            allowContentChangeBoost = true,
            allowScrollBoost = true,
            extendBoostOnWindowChange = true
        )
    }
}