package com.mahmutalperenunal.adaptivehz.core.debug

/**
 * In-memory store used by the accessibility event inspector.
 */
object DebugEventStore {

    private const val MAX_EVENTS = 50

    private val events = ArrayDeque<DebugAccessibilityEvent>()

    /**
     * Keeps only the latest debug events.
     */
    @Synchronized
    fun add(event: DebugAccessibilityEvent) {
        if (events.size >= MAX_EVENTS) {
            events.removeFirst()
        }

        events.addLast(event)
    }

    /**
     * Returns events in reverse chronological order.
     */
    @Synchronized
    fun getAll(): List<DebugAccessibilityEvent> {
        return events.toList().asReversed()
    }

    @Synchronized
    fun clear() {
        events.clear()
    }
}