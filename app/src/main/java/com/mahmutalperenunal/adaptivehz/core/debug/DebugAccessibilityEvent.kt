package com.mahmutalperenunal.adaptivehz.core.debug

/**
 * Debug model used for accessibility event inspection.
 */
data class DebugAccessibilityEvent(
    val timestamp: Long,
    val packageName: String,
    val eventType: String,
    val contentChangeTypes: Int,
    val scrollDeltaX: Int,
    val scrollDeltaY: Int
)