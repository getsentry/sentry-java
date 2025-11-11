package io.sentry.asyncprofiler.provider

import io.sentry.ILogger
import io.sentry.ISentryExecutorService
import io.sentry.NoOpContinuousProfiler
import io.sentry.asyncprofiler.profiling.JavaContinuousProfiler
import one.profiler.AsyncProfiler
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue


class AsyncProfilerContinuousProfilerProviderTest {

  @Test
  fun `provider returns JavaAsyncProfiler if AsyncProfiler can be loaded`() {
    val profiler = AsyncProfilerContinuousProfilerProvider().getContinuousProfiler(
      mock(ILogger::class.java),
      "",
      10,
      mock(ISentryExecutorService::class.java)
    )

    assertTrue(profiler is JavaContinuousProfiler)
  }

  @Test
  fun `provider return NoopProfiler if AsyncProfiler cannot be loaded`() {
   mockStatic(AsyncProfiler::class.java).use {
      it.`when`<Any> { AsyncProfiler.getInstance() }.thenReturn(null)

      val profiler = AsyncProfilerContinuousProfilerProvider().getContinuousProfiler(
        mock(ILogger::class.java),
        "",
        10,
        mock(ISentryExecutorService::class.java)
      )

     assertSame(NoOpContinuousProfiler.getInstance(), profiler)
    }
  }
}
