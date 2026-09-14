package com.mahmutalperenunal.adaptivehz.core.engine.model

/** The effective policy most recently confirmed by the refresh-rate transport. */
internal data class AppliedRefreshState(
    val target: RefreshTarget,
    val packageName: String?,
    val globalMode: AdaptiveHzMode,
    val appMode: AppRefreshProfileMode,
    val customRateHz: Int?
)

internal enum class RefreshTarget {
    SYSTEM_CONTROLLED,
    ADAPTIVE_LOW,
    ADAPTIVE_HIGH,
    FORCE_MINIMUM,
    FORCE_MAXIMUM
}

/**
 * Prevents noisy Accessibility events from repeating an already-confirmed system operation.
 * Failed operations are deliberately not recorded, allowing a later signal to retry.
 */
internal class AppliedRefreshStateTracker {
    private var applied: AppliedRefreshState? = null

    fun shouldApply(target: AppliedRefreshState, force: Boolean): Boolean {
        return force || applied != target
    }

    fun isApplied(target: AppliedRefreshState): Boolean = applied == target

    fun markApplied(target: AppliedRefreshState) {
        applied = target
    }

    fun invalidate() {
        applied = null
    }
}
