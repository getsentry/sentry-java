package io.sentry.android.core

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.protocol.SentryTransaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceAndroidEventProcessorTest {

    private class Fixture {
        val options = SentryAndroidOptions()

        val hub = mock<IHub>()
        val context = TransactionContext("name", "op")
        val tracer = SentryTracer(context, hub)

        fun getSut(tracesSampleRate: Double? = 1.0): PerformanceAndroidEventProcessor {
            options.tracesSampleRate = tracesSampleRate
            whenever(hub.options).thenReturn(options)
            return PerformanceAndroidEventProcessor(options)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `reset instance`() {
        AppStartState.getInstance().resetInstance()
    }

    @Test
    fun `add cold start measurement`() {
        val sut = fixture.getSut()

        var tr = getTransaction()
        setAppStart()

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.containsKey("app_start_cold"))
    }

    @Test
    fun `add warm start measurement`() {
        val sut = fixture.getSut()

        var tr = getTransaction()
        setAppStart(false)

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.containsKey("app_start_warm"))
    }

    @Test
    fun `do not add app start metric twice`() {
        val sut = fixture.getSut()

        var tr1 = getTransaction()
        setAppStart(false)

        tr1 = sut.process(tr1, null)

        var tr2 = getTransaction()
        tr2 = sut.process(tr2, null)

        assertTrue(tr1.measurements.containsKey("app_start_warm"))
        assertTrue(tr2.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if its not ready`() {
        val sut = fixture.getSut()

        var tr = getTransaction()

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if performance is disabled`() {
        val sut = fixture.getSut(tracesSampleRate = null)

        var tr = getTransaction()

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.isEmpty())
    }

    private fun setAppStart(coldStart: Boolean = true) {
        AppStartState.getInstance().isColdStart = coldStart
        AppStartState.getInstance().setAppStartTime(0, Date())
        AppStartState.getInstance().setAppStartEnd()
    }

    private fun getTransaction(): SentryTransaction {
        fixture.tracer.startChild("app.start")
        return SentryTransaction(fixture.tracer)
    }
}
