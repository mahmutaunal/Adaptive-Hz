package com.mahmutalperenunal.adaptivehz.core.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RefreshRateOperationCoordinatorTest {
    @Test
    fun `refresh mutations cannot overlap across workers`() {
        val pool = Executors.newFixedThreadPool(2)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondAttempting = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        try {
            pool.execute {
                RefreshRateOperationCoordinator.run {
                    firstEntered.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                }
            }
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS))

            pool.execute {
                secondAttempting.countDown()
                RefreshRateOperationCoordinator.run {
                    secondEntered.countDown()
                }
            }

            assertTrue(secondAttempting.await(1, TimeUnit.SECONDS))
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            pool.shutdownNow()
        }
    }
}
