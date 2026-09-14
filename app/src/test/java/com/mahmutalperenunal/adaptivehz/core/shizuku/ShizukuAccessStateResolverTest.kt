package com.mahmutalperenunal.adaptivehz.core.shizuku

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuAccessStateResolverTest {
    @Test
    fun `stopped binder is unavailable regardless of stale permission state`() {
        assertEquals(
            ShizukuAccessState.NOT_RUNNING,
            ShizukuAccessStateResolver.resolve(running = false, permissionGranted = false)
        )
        assertEquals(
            ShizukuAccessState.NOT_RUNNING,
            ShizukuAccessStateResolver.resolve(running = false, permissionGranted = true)
        )
    }

    @Test
    fun `running binder distinguishes required and granted permission`() {
        assertEquals(
            ShizukuAccessState.PERMISSION_REQUIRED,
            ShizukuAccessStateResolver.resolve(running = true, permissionGranted = false)
        )
        assertEquals(
            ShizukuAccessState.READY,
            ShizukuAccessStateResolver.resolve(running = true, permissionGranted = true)
        )
    }
}
