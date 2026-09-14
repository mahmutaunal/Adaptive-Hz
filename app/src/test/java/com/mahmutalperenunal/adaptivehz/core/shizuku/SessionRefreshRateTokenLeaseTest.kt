package com.mahmutalperenunal.adaptivehz.core.shizuku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRefreshRateTokenLeaseTest {

    @Test
    fun `active range requires both tokens and an exact range`() {
        val lease = SessionRefreshRateTokenLease<String>()
        lease.activate("min", "max", 80, 80)

        assertTrue(lease.isActiveFor(80, 80))
        assertFalse(lease.isActiveFor(60, 80))
    }

    @Test
    fun `successful release clears complete lease`() {
        val lease = SessionRefreshRateTokenLease<String>()
        lease.activate("min", "max", 10, 80)

        assertTrue(lease.releaseAll { true })
        assertFalse(lease.hasTokens())
        assertFalse(lease.isActiveFor(10, 80))
    }

    @Test
    fun `failed release retains token for a later retry`() {
        val lease = SessionRefreshRateTokenLease<String>()
        lease.activate("min", "max", 60, 60)

        assertFalse(lease.releaseAll { token -> token != "min" })
        assertTrue(lease.hasTokens())
        assertFalse(lease.isActiveFor(60, 60))
        assertTrue(lease.releaseAll { true })
        assertFalse(lease.hasTokens())
    }

    @Test
    fun `partially acquired minimum is retained without becoming active`() {
        val lease = SessionRefreshRateTokenLease<String>()
        lease.retainPartiallyAcquiredMin("min")

        assertTrue(lease.hasTokens())
        assertFalse(lease.isActiveFor(80, 80))
        assertTrue(lease.releaseAll { true })
    }
}
