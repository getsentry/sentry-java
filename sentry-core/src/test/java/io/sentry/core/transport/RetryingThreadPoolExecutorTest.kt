package io.sentry.core.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RetryingThreadPoolExecutorTest {
    private val maxRetries = 5
    private val maxQueueSize = 5
    private var threadPool: RetryingThreadPoolExecutor? = null

    @BeforeTest
    fun setup() {
        val threadFactory = ThreadFactory { r ->
            val t = Thread(r, "RetryingScheduledThreadPoolExecutorTestThread")
            t.isDaemon = true
            t
        }
        val rerunImmediately = io.sentry.core.transport.IBackOffIntervalStrategy { 0L }

        // make sure we have enough threads to handle more than the maximum number of enqueued operations
        // in reality this would not be a problem but the test code needs to synchronize the main thread
        // with a number of jobs. If there weren't enough threads, the main thread could block indefinitely
        // because there wouldn't be enough worker threads to handle all jobs in the queue (because the test
        // code blocks the worker threads).
        threadPool = RetryingThreadPoolExecutor(maxQueueSize + 1, maxRetries, maxQueueSize, threadFactory, rerunImmediately, DiscardPolicy())
    }

    @AfterTest
    fun teardown() {
        threadPool?.shutdownNow()
    }

    @Test
    fun `executes once when finishes ok`() {
        val counter = CountDownLatch(1)
        val actualTimes = AtomicInteger()
        threadPool?.submit {
            counter.countDown()
            actualTimes.incrementAndGet()
        }

        counter.await()
        // wait to see if there are any more attempts
        Thread.sleep(1000)

        assertEquals(1, actualTimes.get(), "Successful task should only be run once.")
    }

    @Test
    fun `retries while failing`() {
        val counter = CountDownLatch(3)
        val actualTimes = AtomicInteger()

        threadPool?.submit {
            actualTimes.incrementAndGet()
            counter.countDown()
            if (counter.count > 0) {
                throw RuntimeException()
            }
        }

        val threeTimes = counter.await(1, TimeUnit.MINUTES)
        // wait to see if there are any more attempts
        Thread.sleep(1000)

        assertTrue(threeTimes, "Should have retried 3 times but didn't in 1 minute.")
        assertEquals(3, actualTimes.get(), "Shouldn't see any more attempts after 3 failures, but saw some")
    }

    @Test
    fun `retries at most maxRetries-times`() {
        val counter = CountDownLatch(maxRetries)
        val actualTimes = AtomicInteger()

        threadPool?.submit {
            counter.countDown()
            actualTimes.incrementAndGet()
            throw RuntimeException()
        }

        counter.await(1, TimeUnit.MINUTES)
        Thread.sleep(1000)

        assertEquals(0, counter.count, "Should have retried max retry times but didn't in 1 minute.")
        assertEquals(maxRetries, actualTimes.get(), "Shouldn't see any more attempts after max retries, but saw some")
    }

    @Test
    fun `honors suggested delay on error`() {
        val counter = CountDownLatch(maxRetries)
        val now = System.currentTimeMillis()
        val delay = 40L

        threadPool?.submit(object : Retryable {
            override fun run() {
                counter.countDown()
                throw RuntimeException()
            }

            override fun getSuggestedRetryDelayMillis(): Long {
                return delay
            }
        })

        counter.await()

        val actualDelay = System.currentTimeMillis() - now

        assertTrue(actualDelay >= (maxRetries - 1) * delay, "Should have waited between invocations based on the suggested failure delay.")
    }

    @Test
    fun `limits the queue size`() {
        // using this we're waiting for the submitted jobs to be unblocked
        val jobBlocker = Object()

        // this is used to wait on the main thread until all the jobs are started
        val sync = CountDownLatch(maxQueueSize)

        // this is used to block the main thread until at least 1 of the jobs has finished
        val atLeastOneFinished = CountDownLatch(1)

        val futures = (1..maxQueueSize).map {
            threadPool?.submit {
                sync.countDown()

                // using the primitive notify/wait enables us to wake up the jobs 1 by 1.
                synchronized(jobBlocker) { jobBlocker.wait() }

                // signal that we're finished
                atLeastOneFinished.countDown()
            }
        }

        // wait for the jobs to start
        sync.await()

        futures.forEach {
            assertNotNull(it)
            assertFalse(it.isCancelled, "No task below the max queue size should be cancelled.")
        }

        var f = threadPool?.submit { synchronized(jobBlocker) { jobBlocker.wait() } }
        assertTrue(f != null && f.isCancelled, "A task above the queue size should have been cancelled.")

        // wake up a single job and wait on the main thread for that to finish
        synchronized(jobBlocker) { jobBlocker.notify() }
        atLeastOneFinished.await()

        // now try to test that the above actually made room in the queue again
        val jobBlocker2 = CountDownLatch(1)
        val sync2 = CountDownLatch(1)

        f = threadPool?.submit { sync2.countDown(); jobBlocker2.await() }
        sync2.await()
        assertFalse(f != null && f.isCancelled, "A task should be successfully enqueued after making a place in the queue")

        synchronized(jobBlocker) { jobBlocker.notifyAll() }
        jobBlocker2.countDown()
    }
}
