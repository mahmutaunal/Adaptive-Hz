package com.mahmutalperenunal.adaptivehz.core.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPromptPolicyTest {
    @Test
    fun `waits for meaningful usage across multiple days`() {
        assertFalse(SupportPromptPolicy.isEligible(false, 2, 2))
        assertFalse(SupportPromptPolicy.isEligible(false, 3, 1))
        assertTrue(SupportPromptPolicy.isEligible(false, 3, 2))
    }

    @Test
    fun `never repeats after being shown`() {
        assertFalse(SupportPromptPolicy.isEligible(true, 20, 10))
    }
}
