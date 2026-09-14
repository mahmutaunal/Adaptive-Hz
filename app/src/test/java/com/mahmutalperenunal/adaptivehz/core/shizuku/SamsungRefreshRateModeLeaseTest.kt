package com.mahmutalperenunal.adaptivehz.core.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungRefreshRateModeLeaseTest {
    @Test
    fun `normal mode is opened for custom session and restored`() {
        var mode = 0
        val writes = mutableListOf<Int>()
        val lease = SamsungRefreshRateModeLease(
            readMode = { mode },
            writeMode = { requested -> writes += requested; mode = requested; true }
        )

        assertTrue(lease.prepareHighRange())
        assertEquals(2, mode)
        assertTrue(lease.restoreOriginal())
        assertEquals(0, mode)
        assertEquals(listOf(2, 0), writes)
    }

    @Test
    fun `token replacements retain the first original mode`() {
        var mode = 1
        val lease = SamsungRefreshRateModeLease(
            readMode = { mode },
            writeMode = { requested -> mode = requested; true }
        )

        assertTrue(lease.prepareHighRange())
        assertTrue(lease.prepareHighRange())
        assertTrue(lease.restoreOriginal())
        assertEquals(1, mode)
    }

    @Test
    fun `failed preparation forgets an unusable snapshot`() {
        var mode = 0
        var allowWrite = false
        val lease = SamsungRefreshRateModeLease(
            readMode = { mode },
            writeMode = { requested ->
                if (allowWrite) mode = requested
                allowWrite
            }
        )

        assertFalse(lease.prepareHighRange())
        allowWrite = true
        mode = 1
        assertTrue(lease.prepareHighRange())
        assertTrue(lease.restoreOriginal())
        assertEquals(1, mode)
    }

    @Test
    fun `unreadable Samsung mode is never overwritten`() {
        var writes = 0
        val lease = SamsungRefreshRateModeLease(
            readMode = { null },
            writeMode = { writes += 1; true }
        )

        assertFalse(lease.prepareHighRange())
        assertEquals(0, writes)
    }
}
