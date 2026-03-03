package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.engine.SettingWrite

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
}