package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingsNamespace
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController

/**
 * Experimental fallback strategy for devices without a dedicated vendor implementation.
 *
 * Behavior may vary across manufacturers and ROM versions.
 */
class OtherStrategy : VendorStrategy {

    override val name: String = "Other / Experimental"

    // Builds the experimental setting write for the lowest refresh-rate mode.
    override fun desiredLow(context: Context): SettingWrite {
        return SettingWrite(
            namespace = SettingsNamespace.SECURE,
            key = RefreshRateController.KEY_REFRESH_MODE,
            intValue = 0,
            label = "secure/refresh_rate_mode=0 (Experimental Low)"
        )
    }

    // Builds the experimental setting write for the highest refresh-rate mode.
    override fun desiredHigh(context: Context): SettingWrite {
        return SettingWrite(
            namespace = SettingsNamespace.SECURE,
            key = RefreshRateController.KEY_REFRESH_MODE,
            intValue = 2,
            label = "secure/refresh_rate_mode=2 (Experimental High)"
        )
    }

    // Builds the experimental setting write that returns control to the system.
    override fun desiredSystemControlled(context: Context): SettingWrite {
        return SettingWrite(
            namespace = SettingsNamespace.SECURE,
            key = RefreshRateController.KEY_REFRESH_MODE,
            intValue = 0,
            label = "secure/refresh_rate_mode=0 (Experimental System)"
        )
    }

    // Provides default tuning values for unsupported or unrecognized vendors.
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