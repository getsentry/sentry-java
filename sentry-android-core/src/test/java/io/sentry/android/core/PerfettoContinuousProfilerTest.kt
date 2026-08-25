package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.IConnectionStatusProvider
import io.sentry.ILogger
import io.sentry.IProfilingCanceledCallback
import io.sentry.IScopes
import io.sentry.ProfileLifecycle
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.TracesSampler
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.protocol.SentryId
import io.sentry.test.DeferredExecutorService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
      whenever(mockPerfettoProfiler.start(any())).thenReturn(true)
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

  private fun captureCanceledCallback(): () -> Runnable? {
    var captured: Runnable? = null
    doAnswer { invocation ->
        captured = invocation.getArgument<Runnable>(0)
        null
      }
      .whenever(fixture.mockPerfettoProfiler)
      .setOnCanceledCallback(any())
    return { captured }
  }

  @Test
  fun `registered callback is notified with the profiler id of the running chunk`() {
    val canceledCallback = captureCanceledCallback()
    val profiler = fixture.getSut()
    val notified = mutableListOf<SentryId>()
    profiler.registerProfilingCanceledCallback(IProfilingCanceledCallback { notified.add(it) })

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val runningProfilerId = profiler.profilerId
    assertThat(runningProfilerId).isNotEqualTo(SentryId.EMPTY_ID)

    canceledCallback()!!.run()

    assertThat(notified).containsExactly(runningProfilerId)
  }

  @Test
  fun `unregistered callback is not notified`() {
    val canceledCallback = captureCanceledCallback()
    val profiler = fixture.getSut()
    val notified = mutableListOf<SentryId>()
    val callback = IProfilingCanceledCallback { notified.add(it) }
    profiler.registerProfilingCanceledCallback(callback)
    profiler.unregisterProfilingCanceledCallback(callback)

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    canceledCallback()!!.run()

    assertThat(notified).isEmpty()
  }

  @Test
  fun `profiler id is assigned before the canceled callback is installed`() {
    // Guards against the OS reporting a failure while start() is still on the stack, which would
    // otherwise notify with an id nobody has been given yet.
    var idAtInstallTime: SentryId? = null
    val profiler = fixture.getSut()
    val notified = mutableListOf<SentryId>()
    profiler.registerProfilingCanceledCallback(IProfilingCanceledCallback { notified.add(it) })
    doAnswer { invocation ->
        idAtInstallTime = profiler.profilerId
        invocation.getArgument<Runnable>(0).run()
        null
      }
      .whenever(fixture.mockPerfettoProfiler)
      .setOnCanceledCallback(any())

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)

    assertThat(idAtInstallTime).isNotEqualTo(SentryId.EMPTY_ID)
    assertThat(notified).containsExactly(idAtInstallTime)
  }

  @Test
  fun `profiler id is restored and callbacks notified when the profiler fails to start`() {
    val profiler = fixture.getSut()
    val notified = mutableListOf<SentryId>()
    profiler.registerProfilingCanceledCallback(IProfilingCanceledCallback { notified.add(it) })
    whenever(fixture.mockPerfettoProfiler.start(any())).thenReturn(false)

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)

    assertFalse(profiler.isRunning)
    assertThat(profiler.profilerId).isEqualTo(SentryId.EMPTY_ID)
    assertThat(notified).hasSize(1)
    assertThat(notified.first()).isNotEqualTo(SentryId.EMPTY_ID)
  }

  @Test
  fun `close while terminating notifies callbacks once and then drops them`() {
    val canceledCallback = captureCanceledCallback()
    val profiler = fixture.getSut()
    val notified = mutableListOf<SentryId>()
    profiler.registerProfilingCanceledCallback(IProfilingCanceledCallback { notified.add(it) })

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val runningProfilerId = profiler.profilerId
    val runnable = canceledCallback()!!

    // The pending chunk is dropped by sendChunk once closed, so callbacks have to hear about it
    profiler.close(true)
    assertThat(notified).containsExactly(runningProfilerId)

    runnable.run()

    assertThat(notified).containsExactly(runningProfilerId)
  }

  @Test
  fun `a throwing callback neither escapes nor stops the remaining callbacks`() {
    val canceledCallback = captureCanceledCallback()
    val profiler = fixture.getSut()
    val notified = mutableListOf<SentryId>()
    profiler.registerProfilingCanceledCallback { throw RuntimeException("listener blew up") }
    profiler.registerProfilingCanceledCallback { notified.add(it) }

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    canceledCallback()!!.run()

    assertThat(notified).hasSize(1)
  }

  @Test
  fun `a throwing callback does not escape the start failure path`() {
    val profiler = fixture.getSut()
    profiler.registerProfilingCanceledCallback { throw RuntimeException("listener blew up") }
    whenever(fixture.mockPerfettoProfiler.start(any())).thenReturn(false)

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)

    assertFalse(profiler.isRunning)
  }

  @Test
  fun `cancellation tears the running chunk down so later transactions cannot reuse the id`() {
    val canceledCallback = captureCanceledCallback()
    val profiler = fixture.getSut()

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    canceledCallback()!!.run()

    assertFalse(profiler.isRunning)
    assertThat(profiler.profilerId).isEqualTo(SentryId.EMPTY_ID)
  }

  @Test
  fun `close without terminating keeps registered callbacks`() {
    val canceledCallback = captureCanceledCallback()
    val profiler = fixture.getSut()
    val notified = mutableListOf<SentryId>()
    profiler.registerProfilingCanceledCallback { notified.add(it) }

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    val runnable = canceledCallback()!!
    profiler.close(false)

    runnable.run()

    assertThat(notified).hasSize(1)
  }
}
