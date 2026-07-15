package com.mahmutalperenunal.adaptivehz.core.engine.strategy

import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendor
import com.mahmutalperenunal.adaptivehz.core.engine.model.DeviceVendorDetector
import com.mahmutalperenunal.adaptivehz.core.engine.model.VendorStrategy

// Provides the appropriate refresh-rate strategy for the current device vendor.
object VendorStrategyProvider {

    // Detects the current device vendor and returns its corresponding strategy.
    fun provide(): VendorStrategy {
        // Select the strategy implementation based on the detected vendor.
        return when (DeviceVendorDetector.detect()) {
            DeviceVendor.SAMSUNG -> SamsungStrategy()
            DeviceVendor.XIAOMI -> XiaomiStrategy()
            DeviceVendor.OTHER -> OtherStrategy()
        }
    }
}