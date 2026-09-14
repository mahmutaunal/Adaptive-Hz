package com.mahmutalperenunal.adaptivehz.core.system

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshRateCapabilitySelectionTest {

    @Test
    fun `normalizes deduplicates and sorts physical modes`() {
        val rates = CustomRefreshRateController.normalizeSupportedRates(
            listOf(120f, 59.94f, 24f, 60f, 0f, 48f, 120f)
        )

        assertEquals(listOf(24, 48, 60, 120), rates)
    }

    @Test
    fun `keeps cached physical modes when live modes become a policy subset`() {
        val rates = CustomRefreshRateController.selectStableSupportedRates(
            liveRates = listOf(80),
            cachedRates = listOf(10, 24, 30, 48, 60, 80, 120)
        )

        assertEquals(listOf(10, 24, 30, 48, 60, 80, 120), rates)
    }

    @Test
    fun `keeps cached physical modes when live discovery is temporarily empty`() {
        val rates = CustomRefreshRateController.selectStableSupportedRates(
            liveRates = emptyList(),
            cachedRates = listOf(60, 120)
        )

        assertEquals(listOf(60, 120), rates)
    }

    @Test
    fun `accepts a live capability list that introduces a new rate`() {
        val rates = CustomRefreshRateController.selectStableSupportedRates(
            liveRates = listOf(60, 90, 120),
            cachedRates = listOf(60, 120)
        )

        assertEquals(listOf(60, 90, 120), rates)
    }
}
