package com.mahmutalperenunal.adaptivehz.core.service

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes slow commands and skips queued work superseded by a newer request.
 *
 * A command already executing cannot be interrupted safely, but its stale result is suppressed and
 * the latest request runs immediately after it. Every request invokes [onSettled], including skipped
 * requests, so BroadcastReceiver pending results can always be completed.
 */
internal class LatestCommandDispatcher(
    private val workerExecutor: Executor,
    private val callbackExecutor: Executor
) {
    private val latestGeneration = AtomicLong(0L)

    fun <T> submit(
        operation: () -> T,
        onComplete: (T) -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onSettled: () -> Unit = {}
    ) {
        val generation = latestGeneration.incrementAndGet()

        try {
            workerExecutor.execute {
                if (generation != latestGeneration.get()) {
                    dispatchCallback(onSettled)
                    return@execute
                }

                val result = runCatching(operation)
                dispatchCallback {
                    if (generation == latestGeneration.get()) {
                        result.fold(onSuccess = onComplete, onFailure = onError)
                    }
                    onSettled()
                }
            }
        } catch (error: Throwable) {
            dispatchCallback {
                if (generation == latestGeneration.get()) onError(error)
                onSettled()
            }
        }
    }

    private fun dispatchCallback(block: () -> Unit) {
        try {
            callbackExecutor.execute(block)
        } catch (_: Throwable) {
            block()
        }
    }
}
