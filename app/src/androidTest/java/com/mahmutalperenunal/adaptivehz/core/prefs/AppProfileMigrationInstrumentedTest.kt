package com.mahmutalperenunal.adaptivehz.core.prefs

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mahmutalperenunal.adaptivehz.core.engine.model.AppRefreshProfileMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppProfileMigrationInstrumentedTest {
    @Test
    fun legacyCustomAdaptiveProfileMigratesToDefaultAndRemovesOrphanedValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = "com.example.adaptivehz.legacy-profile-test"
        val storage = context.getSharedPreferences("adaptive_hz_prefs", Context.MODE_PRIVATE)
        val profileKey = "app_profile_$packageName"
        val flagKey = "app_custom_adaptive_$packageName"
        val lowKey = "app_adaptive_low_rate_$packageName"
        val highKey = "app_adaptive_high_rate_$packageName"

        try {
            storage.edit()
                .putString(profileKey, "ADAPTIVE")
                .putBoolean(flagKey, true)
                .putInt(lowKey, 10)
                .putInt(highKey, 80)
                .commit()

            assertEquals(
                AppRefreshProfileMode.DEFAULT,
                AdaptiveHzPrefs.getAppRefreshProfileMode(context, packageName)
            )
            assertFalse(storage.contains(profileKey))
            assertFalse(storage.contains(flagKey))
            assertFalse(storage.contains(lowKey))
            assertFalse(storage.contains(highKey))
        } finally {
            storage.edit()
                .remove(profileKey)
                .remove(flagKey)
                .remove(lowKey)
                .remove(highKey)
                .commit()
        }
    }
}
