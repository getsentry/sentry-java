package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IConnectionStatusProvider
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ProfileLifecycle
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.TracesSampler
import io.sentry.android.core.internal.profiling.ChunkRecord
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.profiling.ProfileRecordingState
import io.sentry.protocol.SentryId
import io.sentry.test.DeferredExecutorService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
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
    val executor = DeferredExecutorService()
    val mockedSentry = mockStatic(Sentry::class.java)
    val mockLogger = mock<ILogger>()
    val mockTracesSampler = mock<TracesSampler>()
    val mockPerfettoProfiler = mock<PerfettoProfiler>()
    val startedChunks = mutableListOf<ChunkRecord>()
    val frameMetricsCollector: SentryFrameMetricsCollector = mock()

    val scopes: IScopes = mock()

    val options =
      spy(SentryAndroidOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
      }

    val mockTraceFile =
      java.io.File.createTempFile("test-trace", ".pftrace").apply {
        writeBytes(byteArrayOf(0x50, 0x65, 0x72, 0x66))
        deleteOnExit()
      }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
      // The profiler id is created inside PerfettoContinuousProfiler, so it is read from the call
      whenever(mockPerfettoProfiler.start(any(), any(), any())).thenAnswer { invocation ->
        ChunkRecord(invocation.getArgument(1), invocation.getArgument(0)).also {
          startedChunks.add(it)
        }
      }
      doAnswer { invocation ->
          val listener = invocation.getArgument<java.util.function.Consumer<java.io.File?>>(0)
          listener.accept(mockTraceFile)
          null
        }
        .whenever(mockPerfettoProfiler)
        .endAndCollect(any())
    }

    fun getSut(
      optionConfig: ((options: SentryAndroidOptions) -> Unit) = {}
    ): PerfettoContinuousProfiler {
      options.executorService = executor
      optionConfig(options)
      whenever(scopes.options).thenReturn(options)
      return PerfettoContinuousProfiler(
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
  fun `isRunning reflects profiler status`() = fixture.getSut().testIsRunningReflectsStatus(mocks)

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
  fun `profiler sends chunk on each restart`() = fixture.getSut().testSendsChunkOnRestart(mocks)

  @Test fun `profiler sends another chunk on stop`() = fixture.getSut().testSendsChunkOnStop(mocks)

  @Test
  fun `close without terminating stops all profiles after chunk is finished`() =
    fixture.getSut().testCloseWithoutTerminatingStopsAfterChunk(mocks)

  @Test
  fun `profiler does not send chunks after close`() =
    fixture.getSut().testDoesNotSendChunksAfterClose(mocks)

  @Test fun `profiler stops when rate limited`() = fixture.getSut().testStopsWhenRateLimited(mocks)

  @Test
  fun `profiler does not start when rate limited`() =
    fixture.getSut().testDoesNotStartWhenRateLimited(mocks)

  @Test
  fun `profiler does not start when offline`() =
    fixture
      .getSut {
        it.connectionStatusProvider = mock { provider ->
          whenever(provider.connectionStatus)
            .thenReturn(IConnectionStatusProvider.ConnectionStatus.DISCONNECTED)
        }
      }
      .testDoesNotStartWhenOffline(mocks)

  @Test
  fun `manual profiler can be started again after a full start-stop cycle`() =
    fixture.getSut().testCanBeStartedAgainAfterStopCycle(mocks)

  // -- Perfetto-specific tests --

  @Test
  fun `async chunk callback does not restart when stop requested while pending`() {
    val profiler = fixture.getSut()

    // Defer the endAndCollect listener to simulate the OS delivering the trace asynchronously,
    // after the chunk timer already captured the (then-true) restart decision.
    var pendingListener: java.util.function.Consumer<java.io.File?>? = null
    doAnswer { invocation ->
        pendingListener = invocation.getArgument(0)
        null
      }
      .whenever(fixture.mockPerfettoProfiler)
      .endAndCollect(any())

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    // Chunk timer fires: stopInternal(true) captures shouldRestart=true and calls endAndCollect,
    // but the listener is held pending instead of firing inline.
    fixture.executor.runAll()
    assertFalse(profiler.isRunning)
    assertNotNull(pendingListener)

    // A stop is requested while the async callback is still pending.
    profiler.stopProfiler(ProfileLifecycle.MANUAL)

    // The OS now delivers the trace. The callback must honor the late stop and not restart.
    pendingListener!!.accept(fixture.mockTraceFile)
    fixture.executor.runAll()
    assertFalse(
      profiler.isRunning,
      "profiler must not restart when a stop was requested while the callback was pending",
    )
  }

  // -- getProfileRecordingState --

  @Test
  fun `getProfileRecordingState is unknown when no chunk ran at all`() {
    val profiler = fixture.getSut()
    val now = fixture.options.dateProvider.now()

    assertEquals(
      ProfileRecordingState.UNKNOWN,
      profiler.getProfileRecordingState(SentryId(), now, now),
    )
  }

  @Test
  fun `getProfileRecordingState is unknown while a chunk is still running`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val duringChunk = fixture.options.dateProvider.now()

    assertEquals(
      ProfileRecordingState.UNKNOWN,
      profiler.getProfileRecordingState(profiler.profilerId, duringChunk, duringChunk),
    )
  }

  @Test
  fun `getProfileRecordingState is unknown while a chunk is still being collected`() {
    doAnswer { null }.whenever(fixture.mockPerfettoProfiler).endAndCollect(any())
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    val duringChunk = fixture.options.dateProvider.now()

    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()

    assertEquals(
      ProfileRecordingState.UNKNOWN,
      profiler.getProfileRecordingState(profilerId, duringChunk, duringChunk),
    )
  }

  @Test
  fun `getProfileRecordingState is recorded for a window a recorded chunk covers`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    val duringChunk = fixture.options.dateProvider.now()

    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()

    assertEquals(
      ProfileRecordingState.RECORDED,
      profiler.getProfileRecordingState(profilerId, duringChunk, duringChunk),
    )
  }

  @Test
  fun `getProfileRecordingState is not recorded when the chunk produced no trace file`() {
    doAnswer { invocation ->
        invocation.getArgument<java.util.function.Consumer<java.io.File?>>(0).accept(null)
        null
      }
      .whenever(fixture.mockPerfettoProfiler)
      .endAndCollect(any())
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    val duringChunk = fixture.options.dateProvider.now()

    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()

    assertEquals(
      ProfileRecordingState.NOT_RECORDED,
      profiler.getProfileRecordingState(profilerId, duringChunk, duringChunk),
    )
  }

  @Test
  fun `getProfileRecordingState is not recorded once the OS reports a failure for the running chunk`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val duringChunk = fixture.options.dateProvider.now()

    // The profiler marks the record as soon as the OS reports the failure, e.g. on a rate limit
    fixture.startedChunks.last().recordingState = ProfileRecordingState.NOT_RECORDED

    assertEquals(
      ProfileRecordingState.NOT_RECORDED,
      profiler.getProfileRecordingState(profiler.profilerId, duringChunk, duringChunk),
    )
  }

  @Test
  fun `getProfileRecordingState is not recorded for a window after the last chunk`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    val afterChunk = fixture.options.dateProvider.now()

    assertEquals(
      ProfileRecordingState.NOT_RECORDED,
      profiler.getProfileRecordingState(profilerId, afterChunk, afterChunk),
    )
  }

  @Test
  fun `getProfileRecordingState is not recorded for a profiler id the history does not know`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val duringChunk = fixture.options.dateProvider.now()
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()

    assertEquals(
      ProfileRecordingState.NOT_RECORDED,
      profiler.getProfileRecordingState(SentryId(), duringChunk, duringChunk),
    )
  }

  @Test
  fun `getProfileRecordingState is unknown when a failed and an unknown chunk cover the window`() {
    doAnswer { invocation ->
        invocation.getArgument<java.util.function.Consumer<java.io.File?>>(0).accept(null)
        null
      }
      .whenever(fixture.mockPerfettoProfiler)
      .endAndCollect(any())
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    val duringFailedChunk = fixture.options.dateProvider.now()

    // The chunk timer fires, so the failed chunk ends and the next one starts and keeps running
    fixture.executor.runAll()
    val duringRunningChunk = fixture.options.dateProvider.now()

    assertEquals(
      ProfileRecordingState.UNKNOWN,
      profiler.getProfileRecordingState(profilerId, duringFailedChunk, duringRunningChunk),
    )
  }

  @Test
  fun `getProfileRecordingState judges a window that starts before the profiler did`() {
    // An app start transaction is back-dated to before Sentry init, and still has to be judged
    val beforeProfiler = fixture.options.dateProvider.now()
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    val duringChunk = fixture.options.dateProvider.now()
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()

    assertEquals(
      ProfileRecordingState.RECORDED,
      profiler.getProfileRecordingState(profilerId, beforeProfiler, duringChunk),
    )
  }

  @Test
  fun `getProfileRecordingState answers from the chunks left after eviction`() {
    val beforeProfiler = fixture.options.dateProvider.now()
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId

    // Each chunk timer fires a stop and a restart, until the first chunks fall out of the history
    repeat(PerfettoContinuousProfiler.MAX_CHUNK_HISTORY_SIZE + 1) { fixture.executor.runAll() }
    val duringLastChunk = fixture.options.dateProvider.now()

    assertEquals(
      ProfileRecordingState.RECORDED,
      profiler.getProfileRecordingState(profilerId, beforeProfiler, duringLastChunk),
    )
    assertEquals(
      ProfileRecordingState.UNKNOWN,
      profiler.getProfileRecordingState(profilerId, duringLastChunk, duringLastChunk),
      "the last chunk is still running, and the recorded ones before it are outside the window",
    )
  }

  @Test
  fun `getProfileRecordingState judges each chunk of a profiler id on its own`() {
    var isFirstChunk = true
    doAnswer { invocation ->
        val listener = invocation.getArgument<java.util.function.Consumer<java.io.File?>>(0)
        // The first chunk produces no trace file, the ones after it do
        listener.accept(if (isFirstChunk) null else fixture.mockTraceFile)
        isFirstChunk = false
        null
      }
      .whenever(fixture.mockPerfettoProfiler)
      .endAndCollect(any())
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    val duringFailedChunk = fixture.options.dateProvider.now()

    // The chunk timer fires, so the failed chunk ends and the next one starts
    fixture.executor.runAll()
    val duringNextChunk = fixture.options.dateProvider.now()
    // The next chunk ends with a trace file, so it is recorded
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()

    assertEquals(
      ProfileRecordingState.NOT_RECORDED,
      profiler.getProfileRecordingState(profilerId, duringFailedChunk, duringFailedChunk),
    )
    assertEquals(
      ProfileRecordingState.RECORDED,
      profiler.getProfileRecordingState(profilerId, duringNextChunk, duringNextChunk),
    )
    assertEquals(
      ProfileRecordingState.RECORDED,
      profiler.getProfileRecordingState(profilerId, duringFailedChunk, duringNextChunk),
      "a window a recorded chunk covers in part keeps its profiler id",
    )
  }

  @Test
  fun `getProfileRecordingState is not recorded for chunks left pending on close`() {
    doAnswer { null }.whenever(fixture.mockPerfettoProfiler).endAndCollect(any())
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val profilerId = profiler.profilerId
    val duringChunk = fixture.options.dateProvider.now()

    profiler.close(true)

    assertEquals(
      ProfileRecordingState.NOT_RECORDED,
      profiler.getProfileRecordingState(profilerId, duringChunk, duringChunk),
    )
  }

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
