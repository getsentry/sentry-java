package io.sentry.asyncprofiler

import io.sentry.ILogger
import io.sentry.asyncprofiler.profiling.JavaContinuousProfiler
import io.sentry.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.sentry.profiling.ProfilingServiceLoader
import kotlin.test.Test
import org.mockito.kotlin.mock

class JavaContinuousProfilingServiceLoaderTest {
  @Test
  fun loadsAsyncProfilerProfileConverter() {
    val service = ProfilingServiceLoader.loadProfileConverter()
    assert(service is AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter)
  }

  @Test
  fun loadsJavaAsyncProfiler() {
    val logger = mock<ILogger>()

    val service = ProfilingServiceLoader.loadContinuousProfiler(logger, "", 10, null)
    assert(service is JavaContinuousProfiler)
  }
}
