package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController

/**
 * Xiaomi / MIUI / HyperOS implementation.
 *
 * Xiaomi refresh-rate behavior differs between MIUI and HyperOS builds:
 *
 * - MIUI / some older HyperOS builds may use "miui_refresh_rate".
 * - HyperOS 1 may require "user_refresh_rate" under the secure table.
 *
 * The strategy still returns a single SettingWrite to keep the current engine model stable.
 * RefreshRateController handles Xiaomi writes by applying both supported keys.
 */
class XiaomiStrategy : VendorStrategy {
    override val name = "Xiaomi"

    // Resolve device-supported minimum Hz and write it explicitly.
    override fun desiredLow(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (minHz, _) = RefreshRateController.resolveDisplayMinMax(appContext)

        return SettingWrite(
            key = RefreshRateController.KEY_XIAOMI_USER_REFRESH_RATE,
            intValue = minHz,
            label = "user_refresh_rate/miui_refresh_rate=$minHz (Min)"
        )
    }

    // Resolve device-supported maximum Hz and write it explicitly.
    override fun desiredHigh(context: Context): SettingWrite {
        val appContext = context.applicationContext
        val (_, maxHz) = RefreshRateController.resolveDisplayMinMax(appContext)

        return SettingWrite(
            key = RefreshRateController.KEY_XIAOMI_USER_REFRESH_RATE,
            intValue = maxHz,
            label = "user_refresh_rate/miui_refresh_rate=$maxHz (Max)"
        )
    }

    // Restores Xiaomi/HyperOS native refresh-rate handling.
    override fun desiredSystemControlled(context: Context): SettingWrite {
        return SettingWrite(
            key = RefreshRateController.KEY_XIAOMI_USER_REFRESH_RATE,
            intValue = 0,
            label = "user_refresh_rate/miui_refresh_rate=0 (System)"
        )
    }

    // Uses conservative interaction timings for broader compatibility.
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