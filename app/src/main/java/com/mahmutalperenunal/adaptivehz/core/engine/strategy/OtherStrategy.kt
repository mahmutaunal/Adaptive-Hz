package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning

/**
 * Fallback strategy for unsupported or unknown vendors.
 *
 * Assumes Samsung-like behavior using "refresh_rate_mode".
 * This is best-effort and may not work on all devices.
 */
class OtherStrategy : VendorStrategy {
    override val name = "Other"

    // Best-effort LOW mapping using refresh_rate_mode=0
    override fun desiredLow(context: Context) =
        SettingWrite(RefreshRateController.KEY_REFRESH_MODE, 0, "refresh_rate_mode=0 (Low)")

    // Best-effort HIGH mapping using refresh_rate_mode=2
    override fun desiredHigh(context: Context) =
        SettingWrite(RefreshRateController.KEY_REFRESH_MODE, 2, "refresh_rate_mode=2 (High)")

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