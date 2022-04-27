package io.sentry.android.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.test.getCtor
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AndroidTransactionProfilerTest {
    private lateinit var context: Context

    private val className = "io.sentry.android.core.AndroidTransactionProfiler"
    private val ctorTypes = arrayOf(Context::class.java, SentryAndroidOptions::class.java, BuildInfoProvider::class.java)
    private val fixture = Fixture()

    private class Fixture {
        private val mockDsn = "http://key@localhost/proj"
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
        }
        val options = SentryAndroidOptions().apply {
            dsn = mockDsn
            isProfilingEnabled = true
        }
        val transaction1 = SentryTracer(TransactionContext("", ""), mock())
        val transaction2 = SentryTracer(TransactionContext("", ""), mock())

        fun getSut(context: Context, buildInfoProvider: BuildInfoProvider = buildInfo): AndroidTransactionProfiler =
            AndroidTransactionProfiler(context, options, buildInfoProvider)
    }

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        AndroidOptionsInitializer.init(fixture.options, context)
        // Profiler doesn't start if the folder doesn't exists.
        // Usually it's generated when calling Sentry.init, but for tests we can create it manually.
        File(fixture.options.profilingTracesDirPath).mkdirs()
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
            ctor.newInstance(arrayOf(mock<Context>(), null, mock()))
        }
        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(mock<Context>(), mock<SentryAndroidOptions>(), null))
        }
    }

    @Test
    fun `profiler profiles current transaction`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertEquals(fixture.transaction1.eventId.toString(), traceData!!.transactionId)
    }

    @Test
    fun `profiler works only on api 21+`() {
        val buildInfo = mock<BuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.KITKAT)
        }
        val profiler = fixture.getSut(context, buildInfo)
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(traceData)
    }

    @Test
    fun `profiler on isProfilingEnabled false`() {
        fixture.options.apply {
            isProfilingEnabled = false
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(traceData)
    }

    @Test
    fun `profiler on tracesDirPath null`() {
        fixture.options.apply {
            profilingTracesDirPath = null
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(traceData)
    }

    @Test
    fun `profiler on tracesDirPath empty`() {
        fixture.options.apply {
            profilingTracesDirPath = ""
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(traceData)
    }

    @Test
    fun `profiler on profilingTracesIntervalMillis 0`() {
        fixture.options.apply {
            profilingTracesIntervalMillis = 0
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(traceData)
    }

    @Test
    fun `onTransactionFinish works only if previously started`() {
        val profiler = fixture.getSut(context)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(traceData)
    }

    @Test
    fun `onTransactionFinish returns timedOutData to the timed out transaction once, even after other transactions`() {
        val profiler = fixture.getSut(context)

        // Start and finish first transaction profiling
        profiler.onTransactionStart(fixture.transaction1)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)

        // Set timed out data
        profiler.setTimedOutProfilingData(traceData)

        // Start and finish second transaction profiling
        profiler.onTransactionStart(fixture.transaction2)
        assertEquals(fixture.transaction2.eventId.toString(), profiler.onTransactionFinish(fixture.transaction2)!!.transactionId)

        // First transaction finishes: timed out data is returned
        val traceData2 = profiler.onTransactionFinish(fixture.transaction1)
        assertEquals(traceData, traceData2)

        // If first transaction is finished again, nothing is returned
        assertNull(profiler.onTransactionFinish(fixture.transaction1))
    }

    @Test
    fun `profiling stops and returns data only when starting transaction finishes`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        profiler.onTransactionStart(fixture.transaction2)

        var traceData = profiler.onTransactionFinish(fixture.transaction2)
        assertNull(traceData)

        traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertEquals(fixture.transaction1.eventId.toString(), traceData!!.transactionId)
    }
}
