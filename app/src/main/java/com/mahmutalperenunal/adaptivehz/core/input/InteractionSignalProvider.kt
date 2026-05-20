
package com.mahmutalperenunal.adaptivehz.core.input

/**
 * Provides a unified interaction state abstraction for different input monitoring implementations.
 */

interface InteractionSignalProvider {
    fun isTouchActive(): Boolean

    /**
     * Returns whether a touch interaction happened within the given time window.
     */
    fun wasRecentlyTouched(windowMs: Long = 300L): Boolean

    /**
     * Indicates whether the underlying input monitoring backend is currently operational.
     */
    fun isAvailable(): Boolean
}