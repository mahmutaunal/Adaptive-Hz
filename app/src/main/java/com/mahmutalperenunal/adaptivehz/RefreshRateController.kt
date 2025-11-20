package com.mahmutalperenunal.adaptivehz

import android.content.ContentValues.TAG
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Helper that converts the screen refresh rate to an adaptive range of 60–90 Hz via system settings
 * on unsupported Samsung Galaxy devices.
 *
 * MIN_REFRESH_RATE and PEAK_REFRESH_RATE are defined in the AOSP documentation.
 * refresh_rate_mode is a hidden secure key that can also be changed via ADB on Samsung devices:
 * 0 = Normal (60), 1 = Adaptive, 2 = High, etc.
 */
object RefreshRateController {

    // Key used for adaptive/normal/high on Samsung devices
    private const val KEY_REFRESH_MODE = "refresh_rate_mode"

    /**
     * If you want, set the device to a fixed 60 Hz.
     */
    fun applyForce60(context: Context) {
        val cr = context.contentResolver
        try {
            Settings.Secure.putInt(
                cr,
                KEY_REFRESH_MODE,
                0 // Normal / 60
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set 60 Hz mode", e)
        }
    }

    /**
     * If you want, set the device to a fixed 90 Hz.
     */
    fun applyForce90(context: Context) {
        val cr = context.contentResolver
        try {
            Settings.Secure.putInt(
                cr,
                KEY_REFRESH_MODE,
                2 // High
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set High mode", e)
        }
    }
}