package io.sentry.android.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryTracer
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.android.core.ActivityLifecycleIntegration.UI_LOAD_OP
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.SentryTransaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceAndroidEventProcessorTest {

    private class Fixture {
        val options = SentryAndroidOptions()

        val hub = mock<IHub>()
        val context = TransactionContext("name", "op", TracesSamplingDecision(true))
        val tracer = SentryTracer(context, hub)
        val activityFramesTracker = mock<ActivityFramesTracker>()

        fun getSut(tracesSampleRate: Double? = 1.0): PerformanceAndroidEventProcessor {
            options.tracesSampleRate = tracesSampleRate
            whenever(hub.options).thenReturn(options)
            return PerformanceAndroidEventProcessor(options, activityFramesTracker)
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

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.containsKey("app_start_cold"))
    }

    @Test
    fun `add warm start measurement`() {
        val sut = fixture.getSut()

        var tr = getTransaction("app.start.warm")
        setAppStart(false)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.containsKey("app_start_warm"))
    }

    @Test
    fun `do not add app start metric twice`() {
        val sut = fixture.getSut()

        var tr1 = getTransaction()
        setAppStart(false)

        tr1 = sut.process(tr1, Hint())

        var tr2 = getTransaction()
        tr2 = sut.process(tr2, Hint())

        assertTrue(tr1.measurements.containsKey("app_start_warm"))
        assertTrue(tr2.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if its not ready`() {
        val sut = fixture.getSut()

        var tr = getTransaction()

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if performance is disabled`() {
        val sut = fixture.getSut(tracesSampleRate = null)

        var tr = getTransaction()

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if no app_start span`() {
        val sut = fixture.getSut(tracesSampleRate = null)

        var tr = getTransaction("task")

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add slow and frozen frames if not auto transaction`() {
        val sut = fixture.getSut()
        var tr = getTransaction("task")

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add slow and frozen frames if tracing is disabled`() {
        val sut = fixture.getSut(null)
        var tr = getTransaction("task")

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `add slow and frozen frames if auto transaction`() {
        val sut = fixture.getSut()
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.hub)
        var tr = SentryTransaction(tracer)

        val metrics = mapOf("frames_total" to MeasurementValue(1f))
        whenever(fixture.activityFramesTracker.takeMetrics(any())).thenReturn(metrics)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.containsKey("frames_total"))
    }

    private fun setAppStart(coldStart: Boolean = true) {
        AppStartState.getInstance().setColdStart(coldStart)
        AppStartState.getInstance().setAppStartTime(0, Date())
        AppStartState.getInstance().setAppStartEnd()
    }

    private fun getTransaction(op: String = "app.start.cold"): SentryTransaction {
        fixture.tracer.startChild(op)
        return SentryTransaction(fixture.tracer)
    }
}
