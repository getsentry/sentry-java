package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IHub
import io.sentry.ILogger
import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.test.DeferredExecutorService
import io.sentry.test.getProperty
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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

        val hub: IHub = mock()
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
            whenever(hub.options).thenReturn(options)
            transaction1 = SentryTracer(TransactionContext("", ""), hub)
            transaction2 = SentryTracer(TransactionContext("", ""), hub)
            transaction3 = SentryTracer(TransactionContext("", ""), hub)
            return AndroidContinuousProfiler(
                buildInfoProvider,
                frameMetricsCollector,
                options.logger,
                options.profilingTracesDirPath,
                options.isProfilingEnabled,
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
    fun `profiler on profilesSampleRate=0 false`() {
        val profiler = fixture.getSut {
            it.profilesSampleRate = 0.0
        }
        profiler.start()
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `profiler evaluates if profiling is enabled in options only on first start`() {
        // We create the profiler, and nothing goes wrong
        val profiler = fixture.getSut {
            it.profilesSampleRate = 0.0
        }
        verify(fixture.mockLogger, never()).log(SentryLevel.INFO, "Profiling is disabled in options.")

        // Regardless of how many times the profiler is started, the option is evaluated and logged only once
        profiler.start()
        profiler.start()
        verify(fixture.mockLogger, times(1)).log(SentryLevel.INFO, "Profiling is disabled in options.")
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

        val closeFuture = profiler.closeFuture
        assertNotNull(closeFuture)
        assertTrue(closeFuture.isCancelled)
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
}
