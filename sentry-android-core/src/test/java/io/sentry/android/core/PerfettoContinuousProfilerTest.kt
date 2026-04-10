package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IConnectionStatusProvider
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ProfileLifecycle
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.TracesSampler
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.test.DeferredExecutorService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
  private lateinit var mocks: ProfilerMocks

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
    val frameMetricsCollector: SentryFrameMetricsCollector = mock()

    val scopes: IScopes = mock()

    val options =
      spy(SentryAndroidOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
      }

    val mockTraceFile = java.io.File.createTempFile("test-trace", ".pftrace").apply {
      writeBytes(byteArrayOf(0x50, 0x65, 0x72, 0x66))
      deleteOnExit()
    }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
      whenever(mockPerfettoProfiler.start(any())).thenReturn(true)
      whenever(mockPerfettoProfiler.endAndCollect()).thenReturn(mockTraceFile)
    }

    fun getSut(
      optionConfig: ((options: SentryAndroidOptions) -> Unit) = {},
    ): PerfettoContinuousProfiler {
      options.executorService = executor
      optionConfig(options)
      whenever(scopes.options).thenReturn(options)
      return PerfettoContinuousProfiler(
        buildInfo,
        mockLogger,
        frameMetricsCollector,
        { options.executorService },
        { mockPerfettoProfiler },
      )
    }
  }

  @BeforeTest
  fun `set up`() {
    context = ApplicationProvider.getApplicationContext()
    Sentry.setCurrentScopes(fixture.scopes)
    fixture.mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
    mocks =
      ProfilerMocks(fixture.executor, fixture.mockTracesSampler, fixture.mockLogger, fixture.scopes)
  }

  @AfterTest
  fun clear() {
    fixture.mockedSentry.close()
  }

  // -- Shared tests (see ContinuousProfilerTestCases.kt) --

  @Test
  fun `isRunning reflects profiler status`() =
    fixture.getSut().testIsRunningReflectsStatus(mocks)

  @Test
  fun `stopProfiler stops the profiler after chunk is finished`() =
    fixture.getSut().testStopProfilerStopsAfterChunkFinished(mocks)

  @Test
  fun `profiler multiple starts are accepted in trace mode`() =
    fixture.getSut().testMultipleStartsAcceptedInTraceMode(mocks)

  @Test
  fun `profiler logs a warning on start if not sampled`() =
    fixture.getSut().testLogsWarningIfNotSampled(mocks)

  @Test
  fun `profiler evaluates sessionSampleRate only the first time`() =
    fixture.getSut().testEvaluatesSessionSampleRateOnlyOnce(mocks)

  @Test
  fun `when reevaluateSampling, profiler evaluates sessionSampleRate on next start`() =
    fixture.getSut().testReevaluateSamplingOnNextStart(mocks)

  @Test
  fun `profiler ignores profilesSampleRate`() {
    val profiler = fixture.getSut { it.profilesSampleRate = 0.0 }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
  }

  @Test
  fun `profiler stops and restart for each chunk`() =
    fixture.getSut().testStopsAndRestartsForEachChunk(mocks)

  @Test
  fun `profiler sends chunk on each restart`() =
    fixture.getSut().testSendsChunkOnRestart(mocks)

  @Test
  fun `profiler sends another chunk on stop`() =
    fixture.getSut().testSendsChunkOnStop(mocks)

  @Test
  fun `close without terminating stops all profiles after chunk is finished`() =
    fixture.getSut().testCloseWithoutTerminatingStopsAfterChunk(mocks)

  @Test
  fun `profiler does not send chunks after close`() =
    fixture.getSut().testDoesNotSendChunksAfterClose(mocks)

  @Test
  fun `profiler stops when rate limited`() =
    fixture.getSut().testStopsWhenRateLimited(mocks)

  @Test
  fun `profiler does not start when rate limited`() =
    fixture.getSut().testDoesNotStartWhenRateLimited(mocks)

  @Test
  fun `profiler does not start when offline`() =
    fixture.getSut {
      it.connectionStatusProvider = mock { provider ->
        whenever(provider.connectionStatus)
          .thenReturn(IConnectionStatusProvider.ConnectionStatus.DISCONNECTED)
      }
    }.testDoesNotStartWhenOffline(mocks)

  @Test
  fun `manual profiler can be started again after a full start-stop cycle`() =
    fixture.getSut().testCanBeStartedAgainAfterStopCycle(mocks)

  // -- Perfetto-specific tests --

  @Test
  fun `profiler multiple starts are ignored in manual mode`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    verify(fixture.mockLogger)
      .log(
        eq(SentryLevel.WARNING),
        eq("Unexpected call to startProfiler(MANUAL) while profiler already running. Skipping."),
      )
  }
}
