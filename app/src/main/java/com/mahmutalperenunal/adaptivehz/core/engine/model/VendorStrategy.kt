package com.mahmutalperenunal.adaptivehz.core.engine.model

import android.content.Context

/**
 * Defines how each vendor maps logical refresh-rate requests
 * to concrete system setting writes.
 */
interface VendorStrategy {

    val name: String

    /**
     * LOW state used by Adaptive mode and FORCE_MIN.
     */
    fun desiredLow(context: Context): SettingWrite

    /**
     * Temporary HIGH state used by Adaptive mode after user interaction.
     */
    fun desiredHigh(context: Context): SettingWrite

    /**
     * Persistent minimum mode.
     *
     * Defaults to desiredLow() so existing Samsung/Other strategies
     * keep their current behavior without requiring any changes.
     */
    fun desiredForceMinimum(context: Context): SettingWrite {
        return desiredLow(context)
    }

    /**
     * Persistent maximum mode.
     *
     * Defaults to desiredHigh() so existing Samsung/Other strategies
     * keep their current behavior without requiring any changes.
     */
    fun desiredForceMaximum(context: Context): SettingWrite {
        return desiredHigh(context)
    }

    /**
     * Restores vendor/system-controlled refresh-rate behavior.
     */
    fun desiredSystemControlled(context: Context): SettingWrite?

    fun tuning(): VendorTuning
}