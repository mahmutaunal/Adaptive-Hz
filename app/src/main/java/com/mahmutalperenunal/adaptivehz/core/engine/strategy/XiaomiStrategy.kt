package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController
import com.mahmutalperenunal.adaptivehz.core.engine.SettingWrite

/**
 * Xiaomi / HyperOS implementation.
 *
 * Unlike Samsung, Xiaomi expects the actual Hz value
 * (e.g. 60 / 120) to be written into "miui_refresh_rate".
 */
class XiaomiStrategy : VendorStrategy {
    override val name = "Xiaomi"

    // Resolve device-supported minimum Hz and write it explicitly
    override fun desiredLow(context: Context): SettingWrite {
        val (minHz, _) = RefreshRateController.resolveDisplayMinMax(context)
        return SettingWrite(
            RefreshRateController.KEY_XIAOMI_REFRESH_RATE,
            minHz,
            "miui_refresh_rate=$minHz (Min)"
        )
    }

    // Resolve device-supported maximum Hz and write it explicitly
    override fun desiredHigh(context: Context): SettingWrite {
        val (_, maxHz) = RefreshRateController.resolveDisplayMinMax(context)
        return SettingWrite(
            RefreshRateController.KEY_XIAOMI_REFRESH_RATE,
            maxHz,
            "miui_refresh_rate=$maxHz (Max)"
        )
    }
}