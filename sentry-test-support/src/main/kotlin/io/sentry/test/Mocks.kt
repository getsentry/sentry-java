// ktlint-disable filename
package io.sentry.test

import io.sentry.ISentryExecutorService
import io.sentry.backpressure.IBackpressureMonitor
import org.mockito.kotlin.mock
import java.util.concurrent.Callable
import java.util.concurrent.Future

class ImmediateExecutorService : ISentryExecutorService {
    override fun submit(runnable: Runnable): Future<*> {
        if (runnable !is IBackpressureMonitor) {
            runnable.run()
        }
        return mock()
    }

    override fun <T> submit(callable: Callable<T>): Future<T> = mock()
    override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
        if (runnable !is IBackpressureMonitor) {
            runnable.run()
        }
        return mock<Future<Runnable>>()
    }
    override fun close(timeoutMillis: Long) {}
    override fun isClosed(): Boolean = false
}

class DeferredExecutorService : ISentryExecutorService {

    private var runnables = ArrayList<Runnable>()
    private var scheduledRunnables = ArrayList<Runnable>()

    fun runAll() {
        // take a snapshot of the runnable list in case
        // executing the runnable itself schedules more runnables
        val currentRunnableList = runnables
        val currentScheduledRunnableList = scheduledRunnables

        synchronized(this) {
            runnables = ArrayList()
            scheduledRunnables = ArrayList()
        }

        currentRunnableList.forEach { it.run() }
        currentScheduledRunnableList.forEach { it.run() }
    }

    override fun submit(runnable: Runnable): Future<*> {
        synchronized(this) {
            runnables.add(runnable)
        }
        return mock()
    }

    override fun <T> submit(callable: Callable<T>): Future<T> = mock()
    override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
        synchronized(this) {
            scheduledRunnables.add(runnable)
        }
        return mock()
    }
    override fun close(timeoutMillis: Long) {}
    override fun isClosed(): Boolean = false

    fun hasScheduledRunnables(): Boolean = scheduledRunnables.isNotEmpty()
}
