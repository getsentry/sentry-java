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
    override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> = mock()
    override fun close(timeoutMillis: Long) {}
}
