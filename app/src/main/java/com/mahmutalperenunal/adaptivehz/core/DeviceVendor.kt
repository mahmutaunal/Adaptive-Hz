package com.mahmutalperenunal.adaptivehz.core

import android.os.Build

/**
 * Represents the detected device vendor category.
 *
 * This is used to decide which system-specific refresh rate
 * control mechanism should be applied at runtime.
 */
enum class DeviceVendor {
    /** Samsung devices (One UI) */
    SAMSUNG,

    /** Xiaomi / Redmi / POCO devices (MIUI / HyperOS) */
    XIAOMI,

    /** Any other or unknown vendor */
    OTHER
}

/**
 * Utility object responsible for detecting the current device vendor
 * based on system build information.
 *
 * The result is used by {@link RefreshRateController} to apply
 * vendor-specific secure settings keys without relying on
 * trial-and-error or repeated fallback logic.
 */
object DeviceVendorDetector {

    /**
     * Detects the device vendor using {@link Build#MANUFACTURER} and {@link Build#BRAND}.
     *
     * @return The resolved {@link DeviceVendor} value.
     */
    fun detect(): DeviceVendor {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        val isSamsung = manufacturer.contains("samsung") || brand.contains("samsung")

        val isXiaomiFamily =
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ||
                    brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")

        return when {
            isXiaomiFamily -> DeviceVendor.XIAOMI
            isSamsung -> DeviceVendor.SAMSUNG
            else -> DeviceVendor.OTHER
        }
    }
}