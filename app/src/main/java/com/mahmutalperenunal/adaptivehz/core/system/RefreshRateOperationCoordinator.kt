package com.mahmutalperenunal.adaptivehz.core.system

/** Serializes transport mutations originating from UI actions, engine events and recovery. */
internal object RefreshRateOperationCoordinator {
    private val lock = Any()

    fun <T> run(block: () -> T): T = synchronized(lock, block)
}
