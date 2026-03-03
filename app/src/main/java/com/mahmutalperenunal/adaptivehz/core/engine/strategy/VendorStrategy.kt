package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import android.content.Context
import com.mahmutalperenunal.adaptivehz.core.engine.SettingWrite

/**
 * Defines how each vendor (Samsung, Xiaomi, etc.) maps
 * logical LOW/HIGH states to actual system setting writes.
 *
 * The engine is vendor-agnostic and delegates concrete
 * refresh rate behavior to implementations of this interface.
 */
interface VendorStrategy {
    val name: String
    fun desiredLow(context: Context): SettingWrite
    fun desiredHigh(context: Context): SettingWrite
}