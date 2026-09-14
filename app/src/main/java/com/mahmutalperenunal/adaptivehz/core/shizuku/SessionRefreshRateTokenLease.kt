package com.mahmutalperenunal.adaptivehz.core.shizuku

/** Process-local ownership state for a pair of session-scoped refresh-rate tokens. */
internal class SessionRefreshRateTokenLease<T : Any> {

    private var minToken: T? = null
    private var maxToken: T? = null
    private var activeMin: Int? = null
    private var activeMax: Int? = null

    fun isActiveFor(minRefreshRate: Int, maxRefreshRate: Int): Boolean {
        return minToken != null && maxToken != null &&
            activeMin == minRefreshRate && activeMax == maxRefreshRate
    }

    fun activate(
        minToken: T,
        maxToken: T,
        minRefreshRate: Int,
        maxRefreshRate: Int
    ) {
        this.minToken = minToken
        this.maxToken = maxToken
        activeMin = minRefreshRate
        activeMax = maxRefreshRate
    }

    fun retainPartiallyAcquiredMin(token: T) {
        minToken = token
        maxToken = null
        activeMin = null
        activeMax = null
    }

    fun hasTokens(): Boolean = minToken != null || maxToken != null

    /** Clears only tokens whose remote release was confirmed, preserving failures for retry. */
    fun releaseAll(release: (T) -> Boolean): Boolean {
        activeMin = null
        activeMax = null

        val minReleased = minToken?.let(release) ?: true
        if (minReleased) minToken = null

        val maxReleased = maxToken?.let(release) ?: true
        if (maxReleased) maxToken = null

        return minReleased && maxReleased
    }
}
