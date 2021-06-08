package io.sentry.android.core

import com.nhaarman.mockitokotlin2.mock
import io.sentry.SentryTracer
import io.sentry.protocol.SentryTransaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceAndroidEventProcessorTest {

    private class Fixture {
        val options = SentryAndroidOptions()

        val tracer = SentryTracer(mock(), mock())

        fun getSut(tracesSampleRate: Double? = 1.0): PerformanceAndroidEventProcessor {
            options.tracesSampleRate = tracesSampleRate
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

        var tr = SentryTransaction(fixture.tracer)
        setAppStart()

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.containsKey("app_start_cold"))
    }

    @Test
    fun `add warm start measurement`() {
        val sut = fixture.getSut()

        var tr = SentryTransaction(fixture.tracer)
        setAppStart(false)

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.containsKey("app_start_warm"))
    }

    @Test
    fun `do not add app start metric twice`() {
        val sut = fixture.getSut()

        var tr1 = SentryTransaction(fixture.tracer)
        setAppStart(false)

        tr1 = sut.process(tr1, null)

        var tr2 = SentryTransaction(fixture.tracer)
        tr2 = sut.process(tr2, null)

        assertTrue(tr1.measurements.containsKey("app_start_warm"))
        assertTrue(tr2.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if its not ready`() {
        val sut = fixture.getSut()

        var tr = SentryTransaction(fixture.tracer)

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if performance is disabled`() {
        val sut = fixture.getSut(tracesSampleRate = null)

        var tr = SentryTransaction(fixture.tracer)

        tr = sut.process(tr, null)

        assertTrue(tr.measurements.isEmpty())
    }

    private fun setAppStart(coldStart: Boolean = true) {
        AppStartState.getInstance().isColdStart = coldStart
        AppStartState.getInstance().setAppStartTime(0, Date())
        AppStartState.getInstance().setAppStartEnd()
    }
}
