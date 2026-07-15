package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingWrite
import com.mahmutalperenunal.adaptivehz.core.engine.model.SettingsNamespace
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorTuning
import com.mahmutalperenunal.adaptivehz.core.system.RefreshRateController

/**
 * Samsung / One UI refresh-rate implementation.
 *
 * Known refresh_rate_mode values:
 * 0 -> Normal / minimum
 * 1 -> System-controlled / adaptive
 * 2 -> High / maximum
 */
class SamsungStrategy : VendorStrategy {

    override val name: String = "Samsung"

    // Returns the configuration that favors the lowest available refresh rate.
    override fun desiredLow(context: Context): SettingWrite {
        return SettingWrite(
            namespace = SettingsNamespace.SECURE,
            key = RefreshRateController.KEY_REFRESH_MODE,
            intValue = 0,
            label = "secure/refresh_rate_mode=0 (Minimum)"
        )
    }

    // Returns the configuration that enables Samsung's adaptive high refresh-rate mode.
    override fun desiredHigh(context: Context): SettingWrite {
        return SettingWrite(
            namespace = SettingsNamespace.SECURE,
            key = RefreshRateController.KEY_REFRESH_MODE,
            intValue = 2,
            label = "secure/refresh_rate_mode=2 (Adaptive High)"
        )
    }

    // Returns the configuration that keeps the display at the highest supported refresh rate.
    override fun desiredForceMaximum(context: Context): SettingWrite {
        return SettingWrite(
            namespace = SettingsNamespace.SECURE,
            key = RefreshRateController.KEY_REFRESH_MODE,
            intValue = 2,
            label = "secure/refresh_rate_mode=2 (Force Maximum)"
        )
    }

    // Returns the configuration that lets the system manage the refresh rate automatically.
    override fun desiredSystemControlled(context: Context): SettingWrite {
        return SettingWrite(
            namespace = SettingsNamespace.SECURE,
            key = RefreshRateController.KEY_REFRESH_MODE,
            intValue = 1,
            label = "secure/refresh_rate_mode=1 (System/Adaptive)"
        )
    }

    // Provides Samsung-specific tuning values for the adaptive refresh-rate engine.
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