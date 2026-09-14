package com.mahmutalperenunal.adaptivehz.core.engine.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomRateSelectionResolverTest {
    @Test
    fun `missing custom values preserve legacy behavior`() {
        val state = CustomRateSelectionState(globalMode = AdaptiveHzMode.ADAPTIVE)
        assertNull(CustomRateSelectionResolver.low(state))
        assertNull(CustomRateSelectionResolver.high(state))
    }

    @Test
    fun `global minimum and maximum resolve independently`() {
        assertEquals(
            48,
            CustomRateSelectionResolver.forceMinimum(
                CustomRateSelectionState(
                    globalMode = AdaptiveHzMode.FORCE_MIN,
                    globalMinimum = 48
                )
            )
        )
        assertEquals(
            80,
            CustomRateSelectionResolver.forceMaximum(
                CustomRateSelectionState(
                    globalMode = AdaptiveHzMode.FORCE_MAX,
                    globalMaximum = 80
                )
            )
        )
    }

    @Test
    fun `global adaptive keeps legacy low and resolves one interaction target`() {
        val state = CustomRateSelectionState(
            globalMode = AdaptiveHzMode.ADAPTIVE,
            globalAdaptiveTarget = 120
        )
        assertNull(CustomRateSelectionResolver.low(state))
        assertEquals(120, CustomRateSelectionResolver.high(state))
    }

    @Test
    fun `default app inherits global custom selection`() {
        val state = CustomRateSelectionState(
            globalMode = AdaptiveHzMode.FORCE_MIN,
            appMode = AppRefreshProfileMode.DEFAULT,
            globalMinimum = 30
        )
        assertEquals(30, CustomRateSelectionResolver.low(state))
    }

    @Test
    fun `system controlled app never receives custom target`() {
        val state = CustomRateSelectionState(
            globalMode = AdaptiveHzMode.ADAPTIVE,
            appMode = AppRefreshProfileMode.SYSTEM_CONTROLLED,
            globalAdaptiveTarget = 120
        )
        assertNull(CustomRateSelectionResolver.low(state))
        assertNull(CustomRateSelectionResolver.high(state))
        assertNull(CustomRateSelectionResolver.forceMinimum(state))
        assertNull(CustomRateSelectionResolver.forceMaximum(state))
    }

    @Test
    fun `per app fixed selections override global values`() {
        val minState = CustomRateSelectionState(
            globalMode = AdaptiveHzMode.ADAPTIVE,
            appMode = AppRefreshProfileMode.FORCE_MIN,
            globalMinimum = 10,
            appMinimum = 48
        )
        val maxState = minState.copy(
            appMode = AppRefreshProfileMode.FORCE_MAX,
            globalMaximum = 120,
            appMaximum = 80
        )
        assertEquals(48, CustomRateSelectionResolver.forceMinimum(minState))
        assertEquals(80, CustomRateSelectionResolver.forceMaximum(maxState))
    }

    @Test
    fun `fixed app mode never leaks the opposite global custom value`() {
        val minimumApp = CustomRateSelectionState(
            globalMode = AdaptiveHzMode.FORCE_MAX,
            appMode = AppRefreshProfileMode.FORCE_MIN,
            globalMaximum = 120,
            appMinimum = 30
        )
        val maximumApp = CustomRateSelectionState(
            globalMode = AdaptiveHzMode.FORCE_MIN,
            appMode = AppRefreshProfileMode.FORCE_MAX,
            globalMinimum = 10,
            appMaximum = 80
        )

        assertEquals(30, CustomRateSelectionResolver.forceMinimum(minimumApp))
        assertNull(CustomRateSelectionResolver.forceMaximum(minimumApp))
        assertNull(CustomRateSelectionResolver.forceMinimum(maximumApp))
        assertEquals(80, CustomRateSelectionResolver.forceMaximum(maximumApp))
    }

}
