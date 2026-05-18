package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning

/**
 * Samsung-specific implementation.
 *
 * Uses the hidden secure key "refresh_rate_mode":
 * 0 = Normal (min)
 * 2 = High (max)
 */
class SamsungStrategy : VendorStrategy {
    override val name = "Samsung"

    // Maps logical LOW to Samsung's "Normal" mode
    override fun desiredLow(context: Context) =
        SettingWrite(RefreshRateController.KEY_REFRESH_MODE, 0, "refresh_rate_mode=0 (Min)")

    // Maps logical HIGH to Samsung's "High" mode
    override fun desiredHigh(context: Context) =
        SettingWrite(RefreshRateController.KEY_REFRESH_MODE, 2, "refresh_rate_mode=2 (Max)")

    // Restores Samsung/OneUI native refresh-rate handling by delegating
    override fun desiredSystemControlled(context: Context): SettingWrite {
        return SettingWrite(
            RefreshRateController.KEY_REFRESH_MODE,
            0,
            "refresh_rate_mode=0 (System)"
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