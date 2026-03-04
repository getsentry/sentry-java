package io.sentry.asyncprofiler.init

import io.sentry.ILogger
import io.sentry.ISentryExecutorService
import io.sentry.NoOpContinuousProfiler
import io.sentry.NoOpProfileConverter
import io.sentry.SentryOptions
import io.sentry.asyncprofiler.profiling.JavaContinuousProfiler
import io.sentry.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.sentry.util.InitUtil
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.mockito.kotlin.mock

class AsyncProfilerInitUtilTest {

  @Test
  fun `initialize Profiler returns no-op profiler if profiling disabled`() {
    val options = SentryOptions()
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is NoOpContinuousProfiler)
  }

  @Test
  fun `initialize Converter returns no-op converter if profiling disabled`() {
    val options = SentryOptions()
    val converter = InitUtil.initializeProfileConverter(options)
    assert(converter is NoOpProfileConverter)
  }

  @Test
  fun `initialize profiler returns the existing profiler from options if already initialized`() {
    val initialProfiler =
      JavaContinuousProfiler(mock<ILogger>(), "", 10, mock<ISentryExecutorService>())
    val options =
      SentryOptions().also {
        it.setProfileSessionSampleRate(1.0)
        it.setContinuousProfiler(initialProfiler)
      }

    val profiler = InitUtil.initializeProfiler(options)
    assertSame(initialProfiler, profiler)
  }

  @Test
  fun `initialize converter returns the existing converter from options if already initialized`() {
    val initialConverter = AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter()
    val options =
      SentryOptions().also {
        it.setProfileSessionSampleRate(1.0)
        it.profilerConverter = initialConverter
      }

    val converter = InitUtil.initializeProfileConverter(options)
    assertSame(initialConverter, converter)
  }

  @Test
  fun `initialize Profiler returns JavaContinuousProfiler if profiling enabled but profiler not yet initialized`() {
    val options = SentryOptions().also { it.setProfileSessionSampleRate(1.0) }
    val profiler = InitUtil.initializeProfiler(options)
    assertSame(profiler, options.continuousProfiler)
    assert(profiler is JavaContinuousProfiler)
  }

  @Test
  fun `initialize Converter returns AsyncProfilerProfileConverterProvider if profiling enabled but profiler not yet initialized`() {
    val options = SentryOptions().also { it.setProfileSessionSampleRate(1.0) }
    val converter = InitUtil.initializeProfileConverter(options)
    assertSame(converter, options.profilerConverter)
    assert(converter is AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter)
  }

  @Test
  fun `initialize profiler uses existing profilingTracesDirPath when set`() {
    val customPath = "/custom/path/to/traces"
    val options =
      SentryOptions().also {
        it.setProfileSessionSampleRate(1.0)
        it.profilingTracesDirPath = customPath
      }
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is JavaContinuousProfiler)
    assertSame(customPath, options.profilingTracesDirPath)
  }

  @Test
  fun `initialize profiler creates and sets profilingTracesDirPath when null`() {
    val options = SentryOptions().also { it.setProfileSessionSampleRate(1.0) }
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is JavaContinuousProfiler)
    assertNotNull(options.profilingTracesDirPath)
    assert(options.profilingTracesDirPath!!.contains("sentry_profiling_traces"))
  }
}
