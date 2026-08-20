package com.mahmutalperenunal.adaptivehz.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun `parses v prefix and build metadata`() {
        assertEquals(
            SemanticVersion.parse("2.2.5"),
            SemanticVersion.parse("v2.2.5+28")
        )
    }

    @Test
    fun `compares numeric components numerically`() {
        assertTrue(version("2.10.0") > version("2.9.9"))
    }

    @Test
    fun `stable release is newer than prerelease`() {
        assertTrue(version("3.0.0") > version("3.0.0-beta.2"))
    }

    @Test
    fun `compares prerelease identifiers according to semantic version rules`() {
        assertTrue(version("3.0.0-beta.10") > version("3.0.0-beta.2"))
        assertTrue(version("3.0.0-rc.1") > version("3.0.0-beta.10"))
    }

    @Test
    fun `rejects non semantic release tags`() {
        assertEquals(null, SemanticVersion.parse("latest"))
        assertEquals(null, SemanticVersion.parse("2.0.0-"))
    }

    private fun version(value: String): SemanticVersion {
        return requireNotNull(SemanticVersion.parse(value))
    }
}
