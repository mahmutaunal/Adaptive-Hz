package com.mahmutalperenunal.adaptivehz.core.engine.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppliedRefreshStateTrackerTest {
    private val minimum = AppliedRefreshState(
        target = RefreshTarget.FORCE_MINIMUM,
        packageName = "com.example.browser",
        globalMode = AdaptiveHzMode.ADAPTIVE,
        appMode = AppRefreshProfileMode.FORCE_MIN,
        customRateHz = 24
    )

    @Test
    fun `confirmed target suppresses duplicate event writes`() {
        val tracker = AppliedRefreshStateTracker()

        assertTrue(tracker.shouldApply(minimum, force = false))
        tracker.markApplied(minimum)

        assertFalse(tracker.shouldApply(minimum, force = false))
        assertTrue(tracker.isApplied(minimum))
    }

    @Test
    fun `package mode and custom rate changes produce new transitions`() {
        val tracker = AppliedRefreshStateTracker()
        tracker.markApplied(minimum)

        assertTrue(
            tracker.shouldApply(
                minimum.copy(packageName = "com.example.reader"),
                force = false
            )
        )
        assertTrue(
            tracker.shouldApply(
                minimum.copy(appMode = AppRefreshProfileMode.FORCE_MAX),
                force = false
            )
        )
        assertTrue(tracker.shouldApply(minimum.copy(customRateHz = 30), force = false))
    }

    @Test
    fun `forced reapply and invalidation bypass confirmed state`() {
        val tracker = AppliedRefreshStateTracker()
        tracker.markApplied(minimum)

        assertTrue(tracker.shouldApply(minimum, force = true))
        tracker.invalidate()
        assertTrue(tracker.shouldApply(minimum, force = false))
    }
}
