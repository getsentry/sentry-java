package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.CpuCollectionData
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.MemoryCollectionData
import io.sentry.PerformanceCollectionData
import io.sentry.ProfilingTraceData
import io.sentry.SentryLevel
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.profilemeasurements.ProfileMeasurement
import io.sentry.test.getCtor
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
import java.util.concurrent.FutureTask
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidTransactionProfilerTest {
    private lateinit var context: Context

    private val className = "io.sentry.android.core.AndroidTransactionProfiler"
    private val ctorTypes = arrayOf(Context::class.java, SentryAndroidOptions::class.java, BuildInfoProvider::class.java, SentryFrameMetricsCollector::class.java)
    private val fixture = Fixture()

    private class Fixture {
        private val mockDsn = "http://key@localhost/proj"
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP_MR1)
        }
        val mockLogger = mock<ILogger>()
        var lastScheduledRunnable: Runnable? = null
        val mockExecutorService = object : ISentryExecutorService {
            override fun submit(runnable: Runnable): Future<*> {
                runnable.run()
                return FutureTask {}
            }
            override fun <T> submit(callable: Callable<T>): Future<T> {
                val futureTask = mock<FutureTask<T>>()
                whenever(futureTask.get()).thenAnswer {
                    return@thenAnswer try {
                        callable.call()
                    } catch (e: Exception) {
                        null
                    }
                }
                return futureTask
            }
            override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
                lastScheduledRunnable = runnable
                return FutureTask {}
            }
            override fun close(timeoutMillis: Long) {}
            override fun isClosed() = false
        }

        val options = spy(SentryAndroidOptions()).apply {
            dsn = mockDsn
            profilesSampleRate = 1.0
            isDebug = true
            setLogger(mockLogger)
            executorService = mockExecutorService
        }

        val scopes: IScopes = mock()
        val frameMetricsCollector: SentryFrameMetricsCollector = mock()

        lateinit var transaction1: SentryTracer
        lateinit var transaction2: SentryTracer
        lateinit var transaction3: SentryTracer

        fun getSut(context: Context, buildInfoProvider: BuildInfoProvider = buildInfo): AndroidTransactionProfiler {
            whenever(scopes.options).thenReturn(options)
            transaction1 = SentryTracer(TransactionContext("", ""), scopes)
            transaction2 = SentryTracer(TransactionContext("", ""), scopes)
            transaction3 = SentryTracer(TransactionContext("", ""), scopes)
            return AndroidTransactionProfiler(context, options, buildInfoProvider, frameMetricsCollector)
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
    fun `when null param is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(null, mock<SentryAndroidOptions>(), mock()))
        }
        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(mock<Context>(), null, mock(), mock()))
        }
        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(mock<Context>(), mock<SentryAndroidOptions>(), null, mock()))
        }
        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(mock<Context>(), mock<SentryAndroidOptions>(), mock(), null))
        }
    }

    @Test
    fun `profiler start update inner counter`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        assertEquals(1, profiler.transactionsCounter)
    }

    @Test
    fun `isStarted reflects inner counter`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        assertEquals(1, profiler.transactionsCounter)
        assertTrue(profiler.isRunning)
        profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertEquals(0, profiler.transactionsCounter)
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `profiler multiple starts are ignored`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.start()
        assertEquals(1, profiler.transactionsCounter)
    }

    @Test
    fun `profiler bind set current transaction`() {
        val profiler = fixture.getSut(context)
        assertEquals(0, profiler.transactionsCounter)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        assertEquals(1, profiler.transactionsCounter)
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertEquals(0, profiler.transactionsCounter)

        assertNotNull(profilingTraceData)
        assertEquals(profilingTraceData.transactionId, fixture.transaction1.eventId.toString())
    }

    @Test
    fun `profiler multiple binds are ignored`() {
        val profiler = fixture.getSut(context)
        assertEquals(0, profiler.transactionsCounter)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        profiler.bindTransaction(fixture.transaction2)
        assertEquals(1, profiler.transactionsCounter)

        val profilingTraceData2 = profiler.onTransactionFinish(fixture.transaction2, null, fixture.options)
        assertEquals(1, profiler.transactionsCounter)
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertEquals(0, profiler.transactionsCounter)

        assertNotNull(profilingTraceData)
        assertNull(profilingTraceData2)
        assertEquals(profilingTraceData.transactionId, fixture.transaction1.eventId.toString())
    }

    @Test
    fun `profiler works only on api 22+`() {
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
        }
        val profiler = fixture.getSut(context, buildInfo)
        profiler.start()
        assertEquals(0, profiler.transactionsCounter)
    }

    @Test
    fun `profiler on profilesSampleRate=0 false`() {
        fixture.options.apply {
            profilesSampleRate = 0.0
        }
        val profiler = fixture.getSut(context)
        profiler.start()
        assertEquals(0, profiler.transactionsCounter)
    }

    @Test
    fun `profiler evaluates if profiling is enabled in options only on first transaction profiling`() {
        fixture.options.apply {
            profilesSampleRate = 0.0
        }

        // We create the profiler, and nothing goes wrong
        val profiler = fixture.getSut(context)
        verify(fixture.mockLogger, never()).log(SentryLevel.INFO, "Profiling is disabled in options.")

        // Regardless of how many times the profiler is started, the option is evaluated and logged only once
        profiler.start()
        profiler.start()
        verify(fixture.mockLogger, times(1)).log(SentryLevel.INFO, "Profiling is disabled in options.")
    }

    @Test
    fun `profiler evaluates profilingTracesDirPath options only on first transaction profiling`() {
        fixture.options.apply {
            cacheDirPath = null
        }

        // We create the profiler, and nothing goes wrong
        val profiler = fixture.getSut(context)
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
    fun `profiler evaluates profilingTracesHz options only on first transaction profiling`() {
        fixture.options.apply {
            profilingTracesHz = 0
        }

        // We create the profiler, and nothing goes wrong
        val profiler = fixture.getSut(context)
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
        fixture.options.apply {
            cacheDirPath = null
        }
        val profiler = fixture.getSut(context)
        profiler.start()
        assertEquals(0, profiler.transactionsCounter)
    }

    @Test
    fun `profiler on tracesDirPath empty`() {
        fixture.options.apply {
            cacheDirPath = null
        }
        val profiler = fixture.getSut(context)
        profiler.start()
        assertEquals(0, profiler.transactionsCounter)
    }

    @Test
    fun `profiler on profilingTracesHz 0`() {
        fixture.options.apply {
            profilingTracesHz = 0
        }
        val profiler = fixture.getSut(context)
        profiler.start()
        assertEquals(0, profiler.transactionsCounter)
    }

    @Test
    fun `profiler never use background threads`() {
        val profiler = fixture.getSut(context)
        val mockExecutorService: ISentryExecutorService = mock()
        fixture.options.executorService = mockExecutorService
        whenever(mockExecutorService.submit(any<Callable<*>>())).thenReturn(mock())
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        verify(mockExecutorService, never()).submit(any<Runnable>())
        val profilingTraceData: ProfilingTraceData? = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNotNull(profilingTraceData)
        verify(mockExecutorService, never()).submit(any<Callable<*>>())
    }

    @Test
    fun `profiling transaction with empty name fallbacks to unknown`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNotNull(profilingTraceData)
        assertEquals("unknown", profilingTraceData.transactionName)
        assertEquals("unknown", profilingTraceData.transactions.first().name)
    }

    @Test
    fun `profiler does not throw if traces cannot be written to disk`() {
        File(fixture.options.profilingTracesDirPath!!).setWritable(false)
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        // We assert that no trace files are written
        assertTrue(File(fixture.options.profilingTracesDirPath!!).list()!!.isEmpty())
        verify(fixture.mockLogger).log(eq(SentryLevel.ERROR), eq("Error while stopping profiling: "), any())
    }

    @Test
    fun `onTransactionFinish works only if previously started`() {
        val profiler = fixture.getSut(context)
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNull(profilingTraceData)
    }

    @Test
    fun `timedOutData is not recorded`() {
        val profiler = fixture.getSut(context)

        // Start and finish first transaction profiling
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)

        // Set timed out data by calling the timeout scheduled job
        fixture.lastScheduledRunnable?.run()

        // First transaction finishes: timed out data is returned
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNull(profilingTraceData)
    }

    @Test
    fun `profiling stops and returns data only when the first transaction finishes`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        profiler.start()
        profiler.bindTransaction(fixture.transaction2)

        var profilingTraceData = profiler.onTransactionFinish(fixture.transaction2, null, fixture.options)
        assertNull(profilingTraceData)

        profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNotNull(profilingTraceData)
        assertEquals(profilingTraceData.transactionId, fixture.transaction1.eventId.toString())
    }

    @Test
    fun `profiling trace data contains release field`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNotNull(profilingTraceData!!.release)
        assertEquals(fixture.options.release, profilingTraceData.release)
    }

    @Test
    fun `profiler starts collecting frame metrics when the first transaction starts`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        verify(fixture.frameMetricsCollector, times(1)).startCollection(any())
        profiler.start()
        verify(fixture.frameMetricsCollector, times(1)).startCollection(any())
    }

    @Test
    fun `profiler stops collecting frame metrics when the first transaction finishes`() {
        val profiler = fixture.getSut(context)
        val frameMetricsCollectorId = "id"
        whenever(fixture.frameMetricsCollector.startCollection(any())).thenReturn(frameMetricsCollectorId)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        profiler.start()
        profiler.bindTransaction(fixture.transaction2)
        profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        verify(fixture.frameMetricsCollector).stopCollection(frameMetricsCollectorId)
    }

    @Test
    fun `profiler does not includes performance measurements when null is passed on transaction finish`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        val data = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertFalse(data!!.measurementsMap.containsKey(ProfileMeasurement.ID_MEMORY_FOOTPRINT))
        assertFalse(data.measurementsMap.containsKey(ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT))
        assertFalse(data.measurementsMap.containsKey(ProfileMeasurement.ID_CPU_USAGE))
    }

    @Test
    fun `profiler includes performance measurements when passed on transaction finish`() {
        val profiler = fixture.getSut(context)
        val performanceCollectionData = ArrayList<PerformanceCollectionData>()
        var singleData = PerformanceCollectionData()
        singleData.addMemoryData(MemoryCollectionData(1, 2, 3))
        singleData.addCpuData(CpuCollectionData(1, 1.4))
        performanceCollectionData.add(singleData)

        singleData = PerformanceCollectionData()
        singleData.addMemoryData(MemoryCollectionData(2, 3, 4))
        performanceCollectionData.add(singleData)

        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        val data = profiler.onTransactionFinish(fixture.transaction1, performanceCollectionData, fixture.options)
        assertContentEquals(
            listOf(1.4),
            data!!.measurementsMap[ProfileMeasurement.ID_CPU_USAGE]!!.values.map { it.value }
        )
        assertContentEquals(
            listOf(2.0, 3.0),
            data.measurementsMap[ProfileMeasurement.ID_MEMORY_FOOTPRINT]!!.values.map { it.value }
        )
        assertContentEquals(
            listOf(3.0, 4.0),
            data.measurementsMap[ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT]!!.values.map { it.value }
        )
    }

    @Test
    fun `profiler stops profiling, clear current transaction and scheduled job on close`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        profiler.bindTransaction(fixture.transaction1)
        assertEquals(1, profiler.transactionsCounter)

        profiler.close()
        assertEquals(0, profiler.transactionsCounter)

        // The timeout scheduled job should be cleared
        val androidProfiler = profiler.getProperty<AndroidProfiler?>("profiler")
        val scheduledJob = androidProfiler?.getProperty<Future<*>?>("scheduledFinish")
        assertNull(scheduledJob)

        // Calling transaction finish returns null, as the profiler was stopped
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNull(profilingTraceData)
    }

    @Test
    fun `profiler stops profiling on close, even if not bound to a transaction`() {
        val profiler = fixture.getSut(context)
        profiler.start()
        assertEquals(1, profiler.transactionsCounter)

        profiler.close()
        assertEquals(0, profiler.transactionsCounter)

        // The timeout scheduled job should be cleared
        val androidProfiler = profiler.getProperty<AndroidProfiler?>("profiler")
        val scheduledJob = androidProfiler?.getProperty<Future<*>?>("scheduledFinish")
        assertNull(scheduledJob)

        // Binding and calling transaction finish returns null, as the profiler was stopped
        profiler.bindTransaction(fixture.transaction1)
        val profilingTraceData = profiler.onTransactionFinish(fixture.transaction1, null, fixture.options)
        assertNull(profilingTraceData)
    }
}
