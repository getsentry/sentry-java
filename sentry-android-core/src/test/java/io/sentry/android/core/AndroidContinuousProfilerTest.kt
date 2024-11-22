package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.CompositePerformanceCollector
import io.sentry.CpuCollectionData
import io.sentry.DataCategory
import io.sentry.IConnectionStatusProvider
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.MemoryCollectionData
import io.sentry.PerformanceCollectionData
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryNanotimeDate
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.profilemeasurements.ProfileMeasurement
import io.sentry.test.DeferredExecutorService
import io.sentry.test.getProperty
import io.sentry.transport.RateLimiter
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Future
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidContinuousProfilerTest {
    private lateinit var context: Context
    private val fixture = Fixture()

    private class Fixture {
        private val mockDsn = "http://key@localhost/proj"
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP_MR1)
        }
        val mockLogger = mock<ILogger>()

        val scopes: IScopes = mock()
        val frameMetricsCollector: SentryFrameMetricsCollector = mock()

        lateinit var transaction1: SentryTracer
        lateinit var transaction2: SentryTracer
        lateinit var transaction3: SentryTracer

        val options = spy(SentryAndroidOptions()).apply {
            dsn = mockDsn
            profilesSampleRate = 1.0
            isDebug = true
            setLogger(mockLogger)
        }

        fun getSut(buildInfoProvider: BuildInfoProvider = buildInfo, optionConfig: ((options: SentryAndroidOptions) -> Unit) = {}): AndroidContinuousProfiler {
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
                options.executorService
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
            buildInfoProvider
        )

        AndroidOptionsInitializer.installDefaultIntegrations(
            context,
            fixture.options,
            buildInfoProvider,
            loadClass,
            activityFramesTracker,
            false,
            false,
            false
        )

        AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
            fixture.options,
            context,
            buildInfoProvider,
            loadClass,
            activityFramesTracker
        )
        // Profiler doesn't start if the folder doesn't exists.
        // Usually it's generated when calling Sentry.init, but for tests we can create it manually.
        File(fixture.options.profilingTracesDirPath!!).mkdirs()

        Sentry.setCurrentScopes(fixture.scopes)
    }

    @AfterTest
    fun clear() {
        context.cacheDir.deleteRecursively()
    }

    @Test
    fun `isRunning reflects profiler status`() {
        val profiler = fixture.getSut()
        profiler.start()
        assertTrue(profiler.isRunning)
        profiler.stop()
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `profiler multiple starts are ignored`() {
        val profiler = fixture.getSut()
        profiler.start()
        assertTrue(profiler.isRunning)
        verify(fixture.mockLogger, never()).log(eq(SentryLevel.WARNING), eq("Profiling has already started..."))
        profiler.start()
        verify(fixture.mockLogger).log(eq(SentryLevel.WARNING), eq("Profiling has already started..."))
        assertTrue(profiler.isRunning)
    }

    @Test
    fun `profiler works only on api 22+`() {
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
        }
        val profiler = fixture.getSut(buildInfo)
        profiler.start()
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `profiler ignores profilesSampleRate`() {
        val profiler = fixture.getSut {
            it.profilesSampleRate = 0.0
        }
        profiler.start()
        assertTrue(profiler.isRunning)
    }

    @Test
    fun `profiler evaluates profilingTracesDirPath options only on first start`() {
        // We create the profiler, and nothing goes wrong
        val profiler = fixture.getSut {
            it.cacheDirPath = null
        }
        verify(fixture.mockLogger, never()).log(
            SentryLevel.WARNING,
            "Disabling profiling because no profiling traces dir path is defined in options."
        )

        // Regardless of how many times the profiler is started, the option is evaluated and logged only once
        profiler.start()
        profiler.start()
        verify(fixture.mockLogger, times(1)).log(
            SentryLevel.WARNING,
            "Disabling profiling because no profiling traces dir path is defined in options."
        )
    }

    @Test
    fun `profiler evaluates profilingTracesHz options only on first start`() {
        // We create the profiler, and nothing goes wrong
        val profiler = fixture.getSut {
            it.profilingTracesHz = 0
        }
        verify(fixture.mockLogger, never()).log(
            SentryLevel.WARNING,
            "Disabling profiling because trace rate is set to %d",
            0
        )

        // Regardless of how many times the profiler is started, the option is evaluated and logged only once
        profiler.start()
        profiler.start()
        verify(fixture.mockLogger, times(1)).log(
            SentryLevel.WARNING,
            "Disabling profiling because trace rate is set to %d",
            0
        )
    }

    @Test
    fun `profiler on tracesDirPath null`() {
        val profiler = fixture.getSut {
            it.cacheDirPath = null
        }
        profiler.start()
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `profiler on tracesDirPath empty`() {
        val profiler = fixture.getSut {
            it.cacheDirPath = ""
        }
        profiler.start()
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `profiler on profilingTracesHz 0`() {
        val profiler = fixture.getSut {
            it.profilingTracesHz = 0
        }
        profiler.start()
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `profiler never use background threads`() {
        val mockExecutorService: ISentryExecutorService = mock()
        val profiler = fixture.getSut {
            it.executorService = mockExecutorService
        }
        whenever(mockExecutorService.submit(any<Callable<*>>())).thenReturn(mock())
        profiler.start()
        verify(mockExecutorService, never()).submit(any<Runnable>())
        profiler.stop()
        verify(mockExecutorService, never()).submit(any<Callable<*>>())
    }

    @Test
    fun `profiler does not throw if traces cannot be written to disk`() {
        val profiler = fixture.getSut {
            File(it.profilingTracesDirPath!!).setWritable(false)
        }
        profiler.start()
        profiler.stop()
        // We assert that no trace files are written
        assertTrue(
            File(fixture.options.profilingTracesDirPath!!)
                .list()!!
                .isEmpty()
        )
        verify(fixture.mockLogger).log(eq(SentryLevel.ERROR), eq("Error while stopping profiling: "), any())
    }

    @Test
    fun `profiler starts performance collector on start`() {
        val performanceCollector = mock<CompositePerformanceCollector>()
        fixture.options.compositePerformanceCollector = performanceCollector
        val profiler = fixture.getSut()
        verify(performanceCollector, never()).start(any<String>())
        profiler.start()
        verify(performanceCollector).start(any<String>())
    }

    @Test
    fun `profiler stops performance collector on stop`() {
        val performanceCollector = mock<CompositePerformanceCollector>()
        fixture.options.compositePerformanceCollector = performanceCollector
        val profiler = fixture.getSut()
        profiler.start()
        verify(performanceCollector, never()).stop(any<String>())
        profiler.stop()
        verify(performanceCollector).stop(any<String>())
    }

    @Test
    fun `profiler stops collecting frame metrics when it stops`() {
        val profiler = fixture.getSut()
        val frameMetricsCollectorId = "id"
        whenever(fixture.frameMetricsCollector.startCollection(any())).thenReturn(frameMetricsCollectorId)
        profiler.start()
        verify(fixture.frameMetricsCollector, never()).stopCollection(frameMetricsCollectorId)
        profiler.stop()
        verify(fixture.frameMetricsCollector).stopCollection(frameMetricsCollectorId)
    }

    @Test
    fun `profiler stops profiling and clear scheduled job on close`() {
        val profiler = fixture.getSut()
        profiler.start()
        assertTrue(profiler.isRunning)

        profiler.close()
        assertFalse(profiler.isRunning)

        // The timeout scheduled job should be cleared
        val androidProfiler = profiler.getProperty<AndroidProfiler?>("profiler")
        val scheduledJob = androidProfiler?.getProperty<Future<*>?>("scheduledFinish")
        assertNull(scheduledJob)

        val stopFuture = profiler.stopFuture
        assertNotNull(stopFuture)
        assertTrue(stopFuture.isCancelled)
    }

    @Test
    fun `profiler stops and restart for each chunk`() {
        val executorService = DeferredExecutorService()
        val profiler = fixture.getSut {
            it.executorService = executorService
        }
        profiler.start()
        assertTrue(profiler.isRunning)

        executorService.runAll()
        verify(fixture.mockLogger).log(eq(SentryLevel.DEBUG), eq("Profile chunk finished. Starting a new one."))
        assertTrue(profiler.isRunning)

        executorService.runAll()
        verify(fixture.mockLogger, times(2)).log(eq(SentryLevel.DEBUG), eq("Profile chunk finished. Starting a new one."))
        assertTrue(profiler.isRunning)
    }

    @Test
    fun `profiler sends chunk on each restart`() {
        val executorService = DeferredExecutorService()
        val profiler = fixture.getSut {
            it.executorService = executorService
        }
        profiler.start()
        assertTrue(profiler.isRunning)
        // We run the executor service to trigger the profiler restart (chunk finish)
        executorService.runAll()
        verify(fixture.scopes, never()).captureProfileChunk(any())
        // Now the executor is used to send the chunk
        executorService.runAll()
        verify(fixture.scopes).captureProfileChunk(any())
    }

    @Test
    fun `profiler sends chunk with measurements`() {
        val executorService = DeferredExecutorService()
        val performanceCollector = mock<CompositePerformanceCollector>()
        val collectionData = PerformanceCollectionData()

        collectionData.addMemoryData(MemoryCollectionData(2, 3, SentryNanotimeDate()))
        collectionData.addCpuData(CpuCollectionData(3.0, SentryNanotimeDate()))
        whenever(performanceCollector.stop(any<String>())).thenReturn(listOf(collectionData))

        fixture.options.compositePerformanceCollector = performanceCollector
        val profiler = fixture.getSut {
            it.executorService = executorService
        }
        profiler.start()
        profiler.stop()
        // We run the executor service to send the profile chunk
        executorService.runAll()
        verify(fixture.scopes).captureProfileChunk(
            check {
                assertContains(it.measurements, ProfileMeasurement.ID_CPU_USAGE)
                assertContains(it.measurements, ProfileMeasurement.ID_MEMORY_FOOTPRINT)
                assertContains(it.measurements, ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT)
            }
        )
    }

    @Test
    fun `profiler sends another chunk on stop`() {
        val executorService = DeferredExecutorService()
        val profiler = fixture.getSut {
            it.executorService = executorService
        }
        profiler.start()
        assertTrue(profiler.isRunning)
        // We run the executor service to trigger the profiler restart (chunk finish)
        executorService.runAll()
        verify(fixture.scopes, never()).captureProfileChunk(any())
        // We stop the profiler, which should send an additional chunk
        profiler.stop()
        // Now the executor is used to send the chunk
        executorService.runAll()
        verify(fixture.scopes, times(2)).captureProfileChunk(any())
    }

    @Test
    fun `profiler does not send chunks after close`() {
        val executorService = DeferredExecutorService()
        val profiler = fixture.getSut {
            it.executorService = executorService
        }
        profiler.start()
        assertTrue(profiler.isRunning)

        // We close the profiler, which should prevent sending additional chunks
        profiler.close()

        // The executor used to send the chunk doesn't do anything
        executorService.runAll()
        verify(fixture.scopes, never()).captureProfileChunk(any())
    }

    @Test
    fun `profiler stops when rate limited`() {
        val executorService = DeferredExecutorService()
        val profiler = fixture.getSut {
            it.executorService = executorService
        }
        val rateLimiter = mock<RateLimiter>()
        whenever(rateLimiter.isActiveForCategory(DataCategory.ProfileChunk)).thenReturn(true)

        profiler.start()
        assertTrue(profiler.isRunning)

        // If the SDK is rate limited, the profiler should stop
        profiler.onRateLimitChanged(rateLimiter)
        assertFalse(profiler.isRunning)
        verify(fixture.mockLogger).log(eq(SentryLevel.WARNING), eq("SDK is rate limited. Stopping profiler."))
    }

    @Test
    fun `profiler does not start when rate limited`() {
        val executorService = DeferredExecutorService()
        val profiler = fixture.getSut {
            it.executorService = executorService
        }
        val rateLimiter = mock<RateLimiter>()
        whenever(rateLimiter.isActiveForCategory(DataCategory.ProfileChunk)).thenReturn(true)
        whenever(fixture.scopes.rateLimiter).thenReturn(rateLimiter)

        // If the SDK is rate limited, the profiler should never start
        profiler.start()
        assertFalse(profiler.isRunning)
        verify(fixture.mockLogger).log(eq(SentryLevel.WARNING), eq("SDK is rate limited. Stopping profiler."))
    }

    @Test
    fun `profiler does not start when offline`() {
        val executorService = DeferredExecutorService()
        val profiler = fixture.getSut {
            it.executorService = executorService
            it.connectionStatusProvider = mock { provider ->
                whenever(provider.connectionStatus).thenReturn(IConnectionStatusProvider.ConnectionStatus.DISCONNECTED)
            }
        }

        // If the device is offline, the profiler should never start
        profiler.start()
        assertFalse(profiler.isRunning)
        verify(fixture.mockLogger).log(eq(SentryLevel.WARNING), eq("Device is offline. Stopping profiler."))
    }
}
