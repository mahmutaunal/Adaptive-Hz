package com.mahmutalperenunal.adaptivehz.core.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

class LatestCommandDispatcherTest {

    @Test
    fun `only latest queued operation executes`() {
        val worker = QueuedExecutor()
        val callbacks = QueuedExecutor()
        val dispatcher = LatestCommandDispatcher(worker, callbacks)
        val executed = mutableListOf<Int>()
        val completed = mutableListOf<Int>()
        var settled = 0

        (1..3).forEach { value ->
            dispatcher.submit(
                operation = { executed += value; value },
                onComplete = { completed += it },
                onSettled = { settled++ }
            )
        }

        worker.runAll()
        callbacks.runAll()

        assertEquals(listOf(3), executed)
        assertEquals(listOf(3), completed)
        assertEquals(3, settled)
    }

    @Test
    fun `stale running result is suppressed but request settles`() {
        val callbacks = QueuedExecutor()
        lateinit var dispatcher: LatestCommandDispatcher
        val completed = mutableListOf<Int>()
        var settled = 0
        val directWorker = Executor { command -> command.run() }

        dispatcher = LatestCommandDispatcher(directWorker, callbacks)
        dispatcher.submit(
            operation = {
                dispatcher.submit(
                    operation = { 2 },
                    onComplete = { completed += it },
                    onSettled = { settled++ }
                )
                1
            },
            onComplete = { completed += it },
            onSettled = { settled++ }
        )
        callbacks.runAll()

        assertEquals(listOf(2), completed)
        assertEquals(2, settled)
    }

    @Test
    fun `operation error is delivered without breaking settlement`() {
        val dispatcher = LatestCommandDispatcher(
            workerExecutor = { it.run() },
            callbackExecutor = { it.run() }
        )
        var errorMessage: String? = null
        var settled = false

        dispatcher.submit(
            operation = { error("boom") },
            onError = { errorMessage = it.message },
            onSettled = { settled = true }
        )

        assertEquals("boom", errorMessage)
        assertEquals(true, settled)
    }

    private class QueuedExecutor : Executor {
        private val commands = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            commands.addLast(command)
        }

        fun runAll() {
            while (commands.isNotEmpty()) commands.removeFirst().run()
        }
    }
}
