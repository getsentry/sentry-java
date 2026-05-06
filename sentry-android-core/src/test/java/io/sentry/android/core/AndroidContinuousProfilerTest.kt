package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.CompositePerformanceCollector
import io.sentry.IConnectionStatusProvider
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.PerformanceCollectionData
import io.sentry.ProfileLifecycle
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryTracer
import io.sentry.TracesSampler
import io.sentry.TransactionContext
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.profilemeasurements.ProfileMeasurement
import io.sentry.test.DeferredExecutorService
import io.sentry.test.getProperty
import java.io.File
import java.util.concurrent.Future
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AndroidContinuousProfilerTest {
  private lateinit var context: Context
  private val fixture = Fixture()
  private lateinit var mocks: ProfilerMocks

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val buildInfo =
      mock<BuildInfoProvider> {
        whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP_MR1)
      }
    val executor = DeferredExecutorService()
    val mockedSentry = mockStatic(Sentry::class.java)
    val mockLogger = mock<ILogger>()
    val mockTracesSampler = mock<TracesSampler>()

    val scopes: IScopes = mock()
    val frameMetricsCollector: SentryFrameMetricsCollector = mock()

    lateinit var transaction1: SentryTracer
    lateinit var transaction2: SentryTracer
    lateinit var transaction3: SentryTracer

    val options =
      spy(SentryAndroidOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
      }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
    }

    fun getSut(
      buildInfoProvider: BuildInfoProvider = buildInfo,
      optionConfig: ((options: SentryAndroidOptions) -> Unit) = {},
    ): AndroidContinuousProfiler {
      options.executorService = executor
      optionConfig(options)
      whenever(scopes.options).thenReturn(options)
      transaction1 = SentryTracer(TransactionContext("", ""), scopes)
      transaction2 = SentryTracer(TransactionContext("", ""), scopes)
      transaction3 = SentryTracer(TransactionContext("", ""), scopes)
      return AndroidContinuousProfiler(
        buildInfoProvider,
        frameMetricsCollector,
        options.logger,
        options.profilingTracesDirPath,
        options.profilingTracesHz,
        { options.executorService },
      )
    }
  }

  @BeforeTest
  fun `set up`() {
    context = ApplicationProvider.getApplicationContext()
    val buildInfoProvider = BuildInfoProvider(fixture.mockLogger)
    val loadClass = LoadClass()
    val activityFramesTracker = ActivityFramesTracker(loadClass, fixture.options)
    AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
      fixture.options,
      context,
      fixture.mockLogger,
      buildInfoProvider,
    )

    AndroidOptionsInitializer.installDefaultIntegrations(
      context,
      fixture.options,
      buildInfoProvider,
      loadClass,
      activityFramesTracker,
      false,
      false,
      false,
      false,
    )

    AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
      fixture.options,
      context,
      buildInfoProvider,
      loadClass,
      activityFramesTracker,
      false,
    )
    // Profiler doesn't start if the folder doesn't exists.
    // Usually it's generated when calling Sentry.init, but for tests we can create it manually.
    File(fixture.options.profilingTracesDirPath!!).mkdirs()

    Sentry.setCurrentScopes(fixture.scopes)

    fixture.mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
    mocks =
      ProfilerMocks(fixture.executor, fixture.mockTracesSampler, fixture.mockLogger, fixture.scopes)
  }

  @AfterTest
  fun clear() {
    context.cacheDir.deleteRecursively()
    fixture.mockedSentry.close()
  }

  // -- TODO: Could be shared with PerfettoContinuousProfiler with some refactoring --

  @Test
  fun `profiler ignores profilesSampleRate`() {
    val profiler = fixture.getSut { it.profilesSampleRate = 0.0 }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
  }

  @Test
  fun `profiler starts performance collector on start`() {
    val performanceCollector = mock<CompositePerformanceCollector>()
    fixture.options.compositePerformanceCollector = performanceCollector
    val profiler = fixture.getSut()
    verify(performanceCollector, never()).start(any<String>())
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(performanceCollector).start(any<String>())
  }

  @Test
  fun `profiler stops performance collector on stop`() {
    val performanceCollector = mock<CompositePerformanceCollector>()
    fixture.options.compositePerformanceCollector = performanceCollector
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(performanceCollector, never()).stop(any<String>())
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    verify(performanceCollector).stop(any<String>())
  }

  @Test
  fun `profiler stops collecting frame metrics when it stops`() {
    val profiler = fixture.getSut()
    val frameMetricsCollectorId = "id"
    whenever(fixture.frameMetricsCollector.startCollection(any()))
      .thenReturn(frameMetricsCollectorId)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.frameMetricsCollector, never()).stopCollection(frameMetricsCollectorId)
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    verify(fixture.frameMetricsCollector).stopCollection(frameMetricsCollectorId)
  }

  @Test
  fun `profiler sends chunk with measurements`() {
    val performanceCollector = mock<CompositePerformanceCollector>()
    val collectionData = PerformanceCollectionData(10)

    collectionData.usedHeapMemory = 2
    collectionData.usedNativeMemory = 3
    collectionData.cpuUsagePercentage = 3.0
    whenever(performanceCollector.stop(any<String>())).thenReturn(listOf(collectionData))

    fixture.options.compositePerformanceCollector = performanceCollector
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    fixture.executor.runAll()
    verify(fixture.scopes)
      .captureProfileChunk(
        check {
          assertContains(it.measurements, ProfileMeasurement.ID_CPU_USAGE)
          assertContains(it.measurements, ProfileMeasurement.ID_MEMORY_FOOTPRINT)
          assertContains(it.measurements, ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT)
        }
      )
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

  // -- Legacy-specific tests (AndroidContinuousProfiler only) --

  @Test
  fun `profiler multiple starts are ignored in manual mode`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    verify(fixture.mockLogger, never())
      .log(eq(SentryLevel.DEBUG), eq("Profiler is already running."))
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockLogger).log(eq(SentryLevel.DEBUG), eq("Profiler is already running."))
    assertTrue(profiler.isRunning)
    assertEquals(0, profiler.rootSpanCounter)
  }

  @Test
  fun `profiler works only on api 22+`() {
    val buildInfo =
      mock<BuildInfoProvider> {
        whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
      }
    val profiler = fixture.getSut(buildInfo)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler evaluates profilingTracesDirPath options only on first start`() {
    val profiler = fixture.getSut { it.cacheDirPath = null }
    verify(fixture.mockLogger, never())
      .log(
        SentryLevel.WARNING,
        "Disabling profiling because no profiling traces dir path is defined in options.",
      )
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockLogger, times(1))
      .log(
        SentryLevel.WARNING,
        "Disabling profiling because no profiling traces dir path is defined in options.",
      )
  }

  @Test
  fun `profiler evaluates profilingTracesHz options only on first start`() {
    val profiler = fixture.getSut { it.profilingTracesHz = 0 }
    verify(fixture.mockLogger, never())
      .log(SentryLevel.WARNING, "Disabling profiling because trace rate is set to %d", 0)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockLogger, times(1))
      .log(SentryLevel.WARNING, "Disabling profiling because trace rate is set to %d", 0)
  }

  @Test
  fun `profiler on tracesDirPath null`() {
    val profiler = fixture.getSut { it.cacheDirPath = null }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler on tracesDirPath empty`() {
    val profiler = fixture.getSut { it.cacheDirPath = "" }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler on profilingTracesHz 0`() {
    val profiler = fixture.getSut { it.profilingTracesHz = 0 }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler does not throw if traces cannot be written to disk`() {
    val profiler = fixture.getSut { File(it.profilingTracesDirPath!!).setWritable(false) }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    assertTrue(File(fixture.options.profilingTracesDirPath!!).list()!!.isEmpty())
    verify(fixture.mockLogger)
      .log(eq(SentryLevel.ERROR), eq("Error while stopping profiling: "), any())
  }

  @Test
  fun `profiler stops profiling and clear scheduled job on close`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    profiler.close(true)
    assertFalse(profiler.isRunning)

    val androidProfiler = profiler.getProperty<AndroidProfiler?>("profiler")
    val scheduledJob = androidProfiler?.getProperty<Future<*>?>("scheduledFinish")
    assertNull(scheduledJob)

    val stopFuture = profiler.stopFuture
    assertNotNull(stopFuture)
    assertTrue(stopFuture.isCancelled || stopFuture.isDone)
  }

  fun withMockScopes(closure: () -> Unit) =
    mockStatic(Sentry::class.java).use {
      it.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
      closure.invoke()
    }
}
