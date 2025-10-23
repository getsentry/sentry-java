import io.sentry.ILogger
import io.sentry.ISentryExecutorService
import io.sentry.NoOpContinuousProfiler
import io.sentry.NoOpProfileConverter
import io.sentry.SentryOptions
import io.sentry.asyncprofiler.profiling.JavaContinuousProfiler
import io.sentry.util.InitUtil
import kotlin.test.Test
import io.sentry.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import org.mockito.kotlin.mock

class AsyncProfilerInitUtilTest {

  @Test
  fun `initialize Profiler returns no-op profiler if profiling disabled`() {
    val options = SentryOptions()
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is NoOpContinuousProfiler)
  }

  @Test
  fun `initialize Converter returns no-op profiler if profiling disabled`() {
    val options = SentryOptions()
    val converter = InitUtil.initializeProfileConverter(options)
    assert(converter is NoOpProfileConverter)
  }

  @Test
  fun `initialize Profiler returns no-op profiler if profiler already initialized`() {
    val options = SentryOptions().also {
      it.setProfileSessionSampleRate(1.0)
      it.tracesSampleRate = 1.0
      it.setContinuousProfiler(
        JavaContinuousProfiler(
          mock<ILogger>(), "", 10,
          mock<ISentryExecutorService>()
        )
      )
    }

    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is NoOpContinuousProfiler)
  }

  @Test
  fun `initialize converter returns no-op converter if converter already initialized`() {
    val options = SentryOptions().also {
      it.setProfileSessionSampleRate(1.0)
      it.tracesSampleRate = 1.0
      it.profilerConverter = AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter()
    }

    val converter = InitUtil.initializeProfileConverter(options)
    assert(converter is NoOpProfileConverter)
  }

  @Test
  fun `initialize Profiler returns JavaContinuousProfiler if profiling enabled but profiler not yet initialized`() {
    val options = SentryOptions().also {
      it.setProfileSessionSampleRate(1.0)
      it.tracesSampleRate = 1.0
    }
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is JavaContinuousProfiler)
  }

  @Test
  fun `initialize Profiler returns AsyncProfilerProfileConverterProvider if profiling enabled but profiler not yet initialized`() {
    val options = SentryOptions().also {
      it.setProfileSessionSampleRate(1.0)
      it.tracesSampleRate = 1.0
    }
    val converter = InitUtil.initializeProfileConverter(options)
    assert(converter is AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter)
  }
}
