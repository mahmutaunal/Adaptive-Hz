package com.mahmutalperenunal.adaptivehz.core.system

import android.os.Build

enum class DeviceVendor { SAMSUNG, XIAOMI, OTHER }

object DeviceVendorDetector {
    fun detect(): DeviceVendor {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()

        val isSamsung = m.contains("samsung") || b.contains("samsung")
        val isXiaomi = m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
                b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")

        return when {
            isXiaomi -> DeviceVendor.XIAOMI
            isSamsung -> DeviceVendor.SAMSUNG
            else -> DeviceVendor.OTHER
        }
    }
}