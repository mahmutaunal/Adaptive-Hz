package com.mahmutalperenunal.adaptivehz.core.engine.model

/**
 * Vendor-specific interaction tuning used by the refresh engine.
 */
data class VendorTuning(
    val interactionIdleTimeoutMs: Long,
    val eventCoalescingWindowMs: Long,
    val allowContentChangeBoost: Boolean,
    val allowScrollBoost: Boolean,
    val extendBoostOnWindowChange: Boolean
)