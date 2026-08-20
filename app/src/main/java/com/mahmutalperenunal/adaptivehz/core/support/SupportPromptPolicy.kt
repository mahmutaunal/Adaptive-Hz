package com.mahmutalperenunal.adaptivehz.core.support

internal object SupportPromptPolicy {
    const val MIN_SESSION_GAP_MS = 6L * 60L * 60L * 1000L
    const val REQUIRED_SESSIONS = 3
    const val REQUIRED_DISTINCT_DAYS = 2

    fun isEligible(
        promptShown: Boolean,
        sessionCount: Int,
        distinctDayCount: Int,
    ): Boolean {
        return !promptShown &&
            sessionCount >= REQUIRED_SESSIONS &&
            distinctDayCount >= REQUIRED_DISTINCT_DAYS
    }
}
