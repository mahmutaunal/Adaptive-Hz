package com.mahmutalperenunal.adaptivehz.core.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperOsRefreshRateSettingLeaseTest {
    @Test
    fun `restores exact existing value after several target changes`() {
        val values = mutableMapOf("user_refresh_rate" to "120")
        val lease = lease(values)

        assertTrue(lease.apply("user_refresh_rate", 90))
        assertTrue(lease.apply("user_refresh_rate", 60))
        assertTrue(lease.restoreOriginal())

        assertEquals("120", values["user_refresh_rate"])
    }

    @Test
    fun `deletes setting that did not exist before lease`() {
        val values = mutableMapOf<String, String>()
        val lease = lease(values)

        assertTrue(lease.apply("miui_refresh_rate", 90))
        assertTrue(lease.restoreOriginal())

        assertFalse(values.containsKey("miui_refresh_rate"))
    }

    @Test
    fun `refuses key change until original key is restored`() {
        val values = mutableMapOf("user_refresh_rate" to "120", "miui_refresh_rate" to "60")
        val lease = lease(values)

        assertTrue(lease.apply("user_refresh_rate", 90))
        assertFalse(lease.apply("miui_refresh_rate", 90))
        assertTrue(lease.restoreOriginal())
        assertTrue(lease.apply("miui_refresh_rate", 90))
    }

    @Test
    fun `retains snapshot when restoration fails so cleanup can retry`() {
        val values = mutableMapOf("user_refresh_rate" to "120")
        var rejectOriginal = true
        val lease = HyperOsRefreshRateSettingLease(
            managedKeys = listOf("user_refresh_rate", "miui_refresh_rate"),
            read = { key -> HyperOsRefreshRateSettingLease.SettingState(values[key]) },
            write = { key, value ->
                if (value == "120" && rejectOriginal) false else {
                    if (value == null) values.remove(key) else values[key] = value
                    true
                }
            }
        )

        assertTrue(lease.apply("user_refresh_rate", 90))
        assertFalse(lease.restoreOriginal())
        rejectOriginal = false
        assertTrue(lease.restoreOriginal())
        assertEquals("120", values["user_refresh_rate"])
    }

    @Test
    fun `restores companion key changed as an OEM side effect`() {
        val values = mutableMapOf("user_refresh_rate" to "120", "miui_refresh_rate" to "60")
        val lease = HyperOsRefreshRateSettingLease(
            managedKeys = listOf("user_refresh_rate", "miui_refresh_rate"),
            read = { key -> HyperOsRefreshRateSettingLease.SettingState(values[key]) },
            write = { key, value ->
                if (value == null) values.remove(key) else values[key] = value
                if (key == "user_refresh_rate" && value == "90") {
                    values["miui_refresh_rate"] = "90"
                }
                true
            }
        )

        assertTrue(lease.apply("user_refresh_rate", 90))
        assertEquals("90", values["miui_refresh_rate"])
        assertTrue(lease.restoreOriginal())
        assertEquals("120", values["user_refresh_rate"])
        assertEquals("60", values["miui_refresh_rate"])
    }

    private fun lease(values: MutableMap<String, String>) =
        HyperOsRefreshRateSettingLease(
            managedKeys = listOf("user_refresh_rate", "miui_refresh_rate"),
            read = { key -> HyperOsRefreshRateSettingLease.SettingState(values[key]) },
            write = { key, value ->
                if (value == null) values.remove(key) else values[key] = value
                true
            }
        )
}
