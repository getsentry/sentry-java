package io.sentry.asyncprofiler.convert

import io.sentry.ILogger
import io.sentry.IProfileConverter
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.TracesSampler
import io.sentry.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.sentry.test.DeferredExecutorService
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import one.profiler.AsyncProfiler
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import kotlin.io.path.deleteIfExists

class JfrAsyncProfilerToSentryProfileConverterTest {

  private val fixture = Fixture()

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val executor = DeferredExecutorService()
    val mockedSentry = Mockito.mockStatic(Sentry::class.java)
    val mockLogger = mock<ILogger>()
    val mockTracesSampler = mock<TracesSampler>()

    val scopes: IScopes = mock()
    val scope: IScope = mock()

    val options =
      spy(SentryOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
      }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
    }

    fun getSut(optionConfig: ((options: SentryOptions) -> Unit) = {}): IProfileConverter? {
      options.executorService = executor
      optionConfig(options)
      whenever(scopes.options).thenReturn(options)
      whenever(scope.options).thenReturn(options)
      return AsyncProfilerProfileConverterProvider().profileConverter
    }
  }

  @BeforeTest
  fun `set up`() {
    Sentry.setCurrentScopes(fixture.scopes)

    fixture.mockedSentry.`when`<IScopes> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
    fixture.mockedSentry.`when`<IScope> { Sentry.getGlobalScope() }.thenReturn(fixture.scope)
  }

  @AfterTest
  fun clear() {
    Sentry.close()
    fixture.mockedSentry.close()
  }

  @Test
  fun `convert async profiler to sentry`() {
    val profiler = AsyncProfiler.getInstance()
    val file = Files.createTempFile("sentry-async-profiler-test", ".jfr")
    val command = String.format("start,jfr,wall=%s,file=%s", "9900us", file.absolutePathString())
    profiler.execute(command)
    profiler.execute("stop,jfr")

    fixture.getSut()!!.convertFromFile(file.toAbsolutePath())
    file.deleteIfExists()
  }
}
