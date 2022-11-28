package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IHub
import io.sentry.ILogger
import io.sentry.ISentryExecutorService
import io.sentry.ProfilingTraceData
import io.sentry.SentryLevel
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.assertEnvelopeItem
import io.sentry.test.getCtor
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
        }
        val mockLogger = mock<ILogger>()
        var lastScheduledRunnable: Runnable? = null
        val mockExecutorService = object : ISentryExecutorService {
            override fun submit(runnable: Runnable): Future<*> {
                runnable.run()
                return FutureTask {}
            }
            override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
                lastScheduledRunnable = runnable
                return FutureTask {}
            }
            override fun close(timeoutMillis: Long) {}
        }

        val options = spy(SentryAndroidOptions()).apply {
            dsn = mockDsn
            profilesSampleRate = 1.0
            isDebug = true
            setLogger(mockLogger)
            executorService = mockExecutorService
        }

        val hub: IHub = mock()
        val frameMetricsCollector: SentryFrameMetricsCollector = mock()

        lateinit var transaction1: SentryTracer
        lateinit var transaction2: SentryTracer
        lateinit var transaction3: SentryTracer

        fun getSut(context: Context, buildInfoProvider: BuildInfoProvider = buildInfo): AndroidTransactionProfiler {
            whenever(hub.options).thenReturn(options)
            transaction1 = SentryTracer(TransactionContext("", ""), hub)
            transaction2 = SentryTracer(TransactionContext("", ""), hub)
            transaction3 = SentryTracer(TransactionContext("", ""), hub)
            return AndroidTransactionProfiler(context, options, buildInfoProvider, frameMetricsCollector, hub)
        }
    }

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        val buildInfoProvider = BuildInfoProvider(fixture.mockLogger)
        AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
            fixture.options,
            context,
            fixture.mockLogger,
            buildInfoProvider
        )
        AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
            fixture.options,
            context,
            buildInfoProvider,
            LoadClass(),
            false,
            false
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
    fun `profiler profiles current transaction`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1)

        verify(fixture.hub).captureEnvelope(
            check {
                assertEquals(1, it.items.count())
                assertEnvelopeItem<ProfilingTraceData>(it.items.toList()) { _, item ->
                    assertEquals(item.transactionId, fixture.transaction1.eventId.toString())
                }
            }
        )
    }

    @Test
    fun `profiler works only on api 21+`() {
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.KITKAT)
        }
        val profiler = fixture.getSut(context, buildInfo)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())
    }

    @Test
    fun `profiler on profilesSampleRate=0 false`() {
        fixture.options.apply {
            profilesSampleRate = 0.0
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())
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
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionStart(fixture.transaction1)
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
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionStart(fixture.transaction1)
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
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionStart(fixture.transaction1)
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
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())
    }

    @Test
    fun `profiler on tracesDirPath empty`() {
        fixture.options.apply {
            cacheDirPath = null
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())
    }

    @Test
    fun `profiler on profilingTracesHz 0`() {
        fixture.options.apply {
            profilingTracesHz = 0
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())
    }

    @Test
    fun `profiler ignores profilingTracesIntervalMillis`() {
        fixture.options.apply {
            profilingTracesIntervalMillis = 0
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNotNull(traceData)
    }

    @Test
    fun `profiler uses background threads`() {
        val profiler = fixture.getSut(context)
        fixture.options.executorService = mock()
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())
    }

    @Test
    fun `onTransactionFinish works only if previously started`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())
    }

    @Test
    fun `timedOutData has timeout truncation reason`() {
        val profiler = fixture.getSut(context)

        // Start and finish first transaction profiling
        profiler.onTransactionStart(fixture.transaction1)

        // Set timed out data by calling the timeout scheduled job
        fixture.lastScheduledRunnable?.run()

        // First transaction finishes: timed out data is returned
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub).captureEnvelope(
            check {
                assertEquals(1, it.items.count())
                assertEnvelopeItem<ProfilingTraceData>(it.items.toList()) { _, item ->
                    assertEquals(item.transactionId, fixture.transaction1.eventId.toString())
                    assertEquals(ProfilingTraceData.TRUNCATION_REASON_TIMEOUT, item.truncationReason)
                }
            }
        )
    }

    @Test
    fun `profiling stops and returns data only when the last transaction finishes`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionStart(fixture.transaction2)

        profiler.onTransactionFinish(fixture.transaction2)
        verify(fixture.hub, never()).captureEnvelope(any())

        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub).captureEnvelope(
            check {
                assertEquals(1, it.items.count())
                assertEnvelopeItem<ProfilingTraceData>(it.items.toList()) { _, item ->
                    assertEquals(item.transactionId, fixture.transaction1.eventId.toString())
                }
            }
        )
    }

    @Test
    fun `profiling records multiple concurrent transactions`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionStart(fixture.transaction2)

        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.hub, never()).captureEnvelope(any())

        profiler.onTransactionStart(fixture.transaction3)
        profiler.onTransactionFinish(fixture.transaction3)
        verify(fixture.hub, never()).captureEnvelope(any())

        profiler.onTransactionFinish(fixture.transaction2)
        verify(fixture.hub).captureEnvelope(
            check {
                val expectedTransactions = listOf(
                    fixture.transaction1.eventId.toString(),
                    fixture.transaction3.eventId.toString(),
                    fixture.transaction2.eventId.toString()
                )

                assertEquals(1, it.items.count())
                assertEnvelopeItem<ProfilingTraceData>(it.items.toList()) { _, item ->
                    assertEquals(item.transactionId, fixture.transaction2.eventId.toString())

                    assertTrue(item.transactions.map { it.id }.containsAll(expectedTransactions))
                    assertTrue(expectedTransactions.containsAll(item.transactions.map { it.id }))
                }
            }
        )
    }

    @Test
    fun `profiler starts collecting frame metrics when the first transaction starts`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        verify(fixture.frameMetricsCollector, times(1)).startCollection(any())
        profiler.onTransactionStart(fixture.transaction2)
        verify(fixture.frameMetricsCollector, times(1)).startCollection(any())
    }

    @Test
    fun `profiler stops collecting frame metrics when the last transaction finishes`() {
        val profiler = fixture.getSut(context)
        val frameMetricsCollectorId = "id"
        whenever(fixture.frameMetricsCollector.startCollection(any())).thenReturn(frameMetricsCollectorId)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionStart(fixture.transaction2)
        profiler.onTransactionFinish(fixture.transaction1)
        verify(fixture.frameMetricsCollector, never()).stopCollection(frameMetricsCollectorId)
        profiler.onTransactionFinish(fixture.transaction2)
        verify(fixture.frameMetricsCollector).stopCollection(frameMetricsCollectorId)
    }
}
