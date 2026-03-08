package com.mahmutalperenunal.adaptivehz.core.system

import android.os.Build

// Supported device vendors with custom refresh-rate handling
enum class DeviceVendor { SAMSUNG, XIAOMI, OTHER }

// Utility responsible for detecting the device manufacturer at runtime
object DeviceVendorDetector {
    // Determines the current device vendor using Build manufacturer and brand fields
    fun detect(): DeviceVendor {
        // Manufacturer and brand are normalized to lowercase for reliable matching
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()

        // Samsung devices usually report "samsung" in manufacturer or brand
        val isSamsung = m.contains("samsung") || b.contains("samsung")
        // Xiaomi ecosystem devices can appear as xiaomi, redmi, or poco
        val isXiaomi = m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
                b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")

        // Priority: Xiaomi first (covers multiple sub-brands), then Samsung
        return when {
            isXiaomi -> DeviceVendor.XIAOMI
            isSamsung -> DeviceVendor.SAMSUNG
            else -> DeviceVendor.OTHER
        }
    }
}