package com.mahmutalperenunal.adaptivehz.core.system

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomRefreshRateControllerInstrumentedTest {
    @Test
    fun applicationContextCanDiscoverPhysicalDisplayModes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val capabilities = CustomRefreshRateController.resolveCapabilities(context)

        assertFalse(capabilities.supportedRates.isEmpty())
    }
}
