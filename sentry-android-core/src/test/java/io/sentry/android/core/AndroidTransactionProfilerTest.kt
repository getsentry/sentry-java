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
    private val ctorTypes = arrayOf(Context::class.java, SentryAndroidOptions::class.java, IBuildInfoProvider::class.java)
    private val fixture = Fixture()
    private lateinit var file: File

    private class Fixture {
        val buildInfo = mock<IBuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
        }
        val options = SentryAndroidOptions().apply {
            isProfilingEnabled = true
        }
        val transaction1 = SentryTracer(TransactionContext("", ""), mock())
        val transaction2 = SentryTracer(TransactionContext("", ""), mock())

        fun getSut(context: Context, buildInfoProvider: IBuildInfoProvider = buildInfo): AndroidTransactionProfiler =
            AndroidTransactionProfiler(context, options, buildInfoProvider)
    }

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        file = context.cacheDir
        AndroidOptionsInitializer.init(fixture.options, createMockContext())
    }

    @AfterTest
    fun clear() {
        file.deleteRecursively()
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
    fun `onTransactionStart stores current transaction`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        assertEquals(fixture.transaction1, profiler.activeTransaction)
    }

    @Test
    fun `onTransactionStart works only on api 21+`() {
        val buildInfo = mock<IBuildInfoProvider> {
            whenever(it.sdkInfoVersion).thenReturn(Build.VERSION_CODES.KITKAT)
        }
        val profiler = fixture.getSut(context, buildInfo)
        profiler.onTransactionStart(fixture.transaction1)
        assertNull(profiler.activeTransaction)
    }

    @Test
    fun `onTransactionStart on isProfilingEnabled false`() {
        fixture.options.apply {
            isProfilingEnabled = false
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        assertNull(profiler.activeTransaction)
    }

    @Test
    fun `onTransactionStart on tracesDirPath null`() {
        fixture.options.apply {
            profilingTracesDirPath = null
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        assertNull(profiler.activeTransaction)
    }

    @Test
    fun `onTransactionStart on tracesDirPath empty`() {
        fixture.options.apply {
            profilingTracesDirPath = ""
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        assertNull(profiler.activeTransaction)
    }

    @Test
    fun `onTransactionStart on profilingTracesIntervalMillis 0`() {
        fixture.options.apply {
            profilingTracesIntervalMillis = 0
        }
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        assertNull(profiler.activeTransaction)
    }

    @Test
    fun `onTransactionFinish works only if previously started`() {
        val profiler = fixture.getSut(context)
        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(profiler.activeTransaction)
        assertNull(traceData)
    }

    @Test
    fun `onTransactionFinish returns timedOutData to the timed out transaction once, even after other transactions`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)

        val traceData = profiler.onTransactionFinish(fixture.transaction1)
        profiler.setTimedOutProfilingData(traceData)
        profiler.onTransactionStart(fixture.transaction2)
        assertEquals(fixture.transaction2.eventId.toString(), profiler.onTransactionFinish(fixture.transaction2)!!.transaction_id)
        val traceData2 = profiler.onTransactionFinish(fixture.transaction1)
        assertEquals(traceData, traceData2)
        assertNull(profiler.onTransactionFinish(fixture.transaction1))
    }

    @Test
    fun `profiling stops and returns data only when starting transaction finishes`() {
        val profiler = fixture.getSut(context)
        profiler.onTransactionStart(fixture.transaction1)
        assertEquals(fixture.transaction1, profiler.activeTransaction)

        profiler.onTransactionStart(fixture.transaction2)
        assertEquals(fixture.transaction1, profiler.activeTransaction)

        var traceData = profiler.onTransactionFinish(fixture.transaction2)
        assertEquals(fixture.transaction1, profiler.activeTransaction)
        assertNull(traceData)

        traceData = profiler.onTransactionFinish(fixture.transaction1)
        assertNull(profiler.activeTransaction)
        assertEquals(fixture.transaction1.eventId.toString(), traceData!!.transaction_id)
    }

    private fun createMockContext(): Context {
        val mockContext = ContextUtilsTest.createMockContext()
        whenever(mockContext.cacheDir).thenReturn(file)
        return mockContext
    }
}
