// ktlint-disable filename
package io.sentry.test

import io.sentry.ISentryExecutorService
import org.mockito.kotlin.mock
import java.util.concurrent.Callable
import java.util.concurrent.Future

class ImmediateExecutorService : ISentryExecutorService {
    override fun submit(runnable: Runnable): Future<*> {
        runnable.run()
        return mock()
    }

    override fun <T> submit(callable: Callable<T>): Future<T> = mock()
    override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
        runnable.run()
        return mock<Future<Runnable>>()
    }
    override fun close(timeoutMillis: Long) {}
    override fun isClosed(): Boolean = false
}

class DeferredExecutorService : ISentryExecutorService {

    private val runnables = ArrayList<Runnable>()
    val scheduledRunnables = ArrayList<Runnable>()

    fun runAll() {
        runnables.forEach { it.run() }
        scheduledRunnables.forEach { it.run() }
    }

    override fun submit(runnable: Runnable): Future<*> {
        runnables.add(runnable)
        return mock()
    }

    override fun <T> submit(callable: Callable<T>): Future<T> = mock()
    override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
        scheduledRunnables.add(runnable)
        return mock()
    }
    override fun close(timeoutMillis: Long) {}
    override fun isClosed(): Boolean = false
}
