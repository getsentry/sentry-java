package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DataCategory
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ProfileLifecycle
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.TracesSampler
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.protocol.SentryId
import io.sentry.test.DeferredExecutorService
import io.sentry.test.injectForField
import io.sentry.transport.RateLimiter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PerfettoContinuousProfilerTest {
  private lateinit var context: Context
  private val fixture = Fixture()

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val buildInfo =
      mock<BuildInfoProvider> {
        whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.VANILLA_ICE_CREAM)
      }
    val executor = DeferredExecutorService()
    val mockedSentry = mockStatic(Sentry::class.java)
    val mockLogger = mock<ILogger>()
    val mockTracesSampler = mock<TracesSampler>()
    val mockPerfettoProfiler = mock<PerfettoProfiler>()

    val scopes: IScopes = mock()
    val frameMetricsCollector: SentryFrameMetricsCollector = mock()

    val options =
      spy(SentryAndroidOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
      }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
      whenever(mockPerfettoProfiler.start(any()))
        .thenReturn(
          AndroidProfiler.ProfileStartData(
            System.nanoTime(),
            0L,
            io.sentry.DateUtils.getCurrentDateTime(),
          )
        )
    }

    fun getSut(): PerfettoContinuousProfiler {
      options.executorService = executor
      whenever(scopes.options).thenReturn(options)
      val profiler =
        PerfettoContinuousProfiler(
          ApplicationProvider.getApplicationContext(),
          buildInfo,
          frameMetricsCollector,
          mockLogger,
          { options.executorService },
        )
      // Inject mock PerfettoProfiler to avoid hitting real ProfilingManager API
      profiler.injectForField("perfettoProfiler", mockPerfettoProfiler)
      profiler.injectForField("isInitialized", true)
      return profiler
    }
  }

  @BeforeTest
  fun `set up`() {
    context = ApplicationProvider.getApplicationContext()
    Sentry.setCurrentScopes(fixture.scopes)
    fixture.mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
  }

  @AfterTest
  fun clear() {
    fixture.mockedSentry.close()
  }

  @Test
  fun `profiler stops when rate limited`() {
    val profiler = fixture.getSut()
    val rateLimiter = mock<RateLimiter>()
    whenever(rateLimiter.isActiveForCategory(DataCategory.ProfileChunkUi)).thenReturn(true)

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    profiler.onRateLimitChanged(rateLimiter)
    assertFalse(profiler.isRunning)
    assertEquals(SentryId.EMPTY_ID, profiler.profilerId)
    assertEquals(SentryId.EMPTY_ID, profiler.chunkId)
    verify(fixture.mockLogger)
      .log(eq(SentryLevel.WARNING), eq("SDK is rate limited. Stopping profiler."))
  }
}
