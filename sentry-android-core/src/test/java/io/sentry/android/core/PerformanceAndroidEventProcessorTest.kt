package io.sentry.android.core

import io.sentry.Hint
import io.sentry.IHub
import io.sentry.MeasurementUnit
import io.sentry.SentryTracer
import io.sentry.SpanContext
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.android.core.ActivityLifecycleIntegration.APP_START_COLD
import io.sentry.android.core.ActivityLifecycleIntegration.UI_LOAD_OP
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.core.performance.AppStartMetrics.AppStartType
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceAndroidEventProcessorTest {

    private class Fixture {
        val options = SentryAndroidOptions()

        val hub = mock<IHub>()
        val context = TransactionContext("name", "op", TracesSamplingDecision(true))
        lateinit var tracer: SentryTracer
        val activityFramesTracker = mock<ActivityFramesTracker>()

        fun getSut(
            tracesSampleRate: Double? = 1.0,
            enableStarfish: Boolean = false
        ): PerformanceAndroidEventProcessor {
            options.tracesSampleRate = tracesSampleRate
            options.isEnableStarfish = enableStarfish
            whenever(hub.options).thenReturn(options)
            tracer = SentryTracer(context, hub)
            return PerformanceAndroidEventProcessor(options, activityFramesTracker)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `reset instance`() {
        AppStartMetrics.getInstance().clear()
    }

    @Test
    fun `add cold start measurement`() {
        val sut = fixture.getSut()

        var tr = getTransaction(AppStartType.COLD)
        setAppStart(fixture.options)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.containsKey(MeasurementValue.KEY_APP_START_COLD))
    }

    @Test
    fun `add cold start measurement for starfish`() {
        val sut = fixture.getSut()
        fixture.options.isEnableStarfish = true

        var tr = getTransaction(AppStartType.COLD)
        setAppStart(fixture.options)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.containsKey(MeasurementValue.KEY_APP_START_COLD))
    }

    @Test
    fun `add warm start measurement`() {
        val sut = fixture.getSut()

        var tr = getTransaction(AppStartType.WARM)
        setAppStart(fixture.options, false)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.containsKey(MeasurementValue.KEY_APP_START_WARM))
    }

    @Test
    fun `set app cold start unit measurement`() {
        val sut = fixture.getSut()

        var tr = getTransaction(AppStartType.COLD)
        setAppStart(fixture.options)

        tr = sut.process(tr, Hint())

        val measurement = tr.measurements[MeasurementValue.KEY_APP_START_COLD]
        assertEquals("millisecond", measurement?.unit)
    }

    @Test
    fun `do not add app start metric twice`() {
        val sut = fixture.getSut()

        var tr1 = getTransaction(AppStartType.COLD)
        setAppStart(fixture.options, false)

        tr1 = sut.process(tr1, Hint())

        var tr2 = getTransaction(AppStartType.UNKNOWN)
        tr2 = sut.process(tr2, Hint())

        assertTrue(tr1.measurements.containsKey(MeasurementValue.KEY_APP_START_WARM))
        assertTrue(tr2.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if its not ready`() {
        val sut = fixture.getSut()

        var tr = getTransaction(AppStartType.UNKNOWN)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if performance is disabled`() {
        val sut = fixture.getSut(tracesSampleRate = null)

        var tr = getTransaction(AppStartType.COLD)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add app start metric if no app_start span`() {
        val sut = fixture.getSut(tracesSampleRate = null)

        var tr = getTransaction(AppStartType.UNKNOWN)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add slow and frozen frames if not auto transaction`() {
        val sut = fixture.getSut()
        var tr = getTransaction(AppStartType.UNKNOWN)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `do not add slow and frozen frames if tracing is disabled`() {
        val sut = fixture.getSut(null)
        var tr = getTransaction(AppStartType.UNKNOWN)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.isEmpty())
    }

    @Test
    fun `add slow and frozen frames if auto transaction`() {
        val sut = fixture.getSut()
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.hub)
        var tr = SentryTransaction(tracer)

        val metrics = mapOf(
            MeasurementValue.KEY_FRAMES_TOTAL to MeasurementValue(
                1f,
                MeasurementUnit.Duration.MILLISECOND.apiName()
            )
        )
        whenever(fixture.activityFramesTracker.takeMetrics(any())).thenReturn(metrics)

        tr = sut.process(tr, Hint())

        assertTrue(tr.measurements.containsKey(MeasurementValue.KEY_FRAMES_TOTAL))
    }

    @Test
    fun `adds app start metrics to app start txn`() {
        // given some app start metrics
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.COLD
        appStartMetrics.appStartTimeSpan.setStartedAt(123)
        appStartMetrics.appStartTimeSpan.setStoppedAt(456)

        val activityTimeSpan = ActivityLifecycleTimeSpan()
        activityTimeSpan.onCreate.description = "MainActivity.onCreate"
        activityTimeSpan.onStart.description = "MainActivity.onStart"

        activityTimeSpan.onCreate.setStartedAt(200)
        activityTimeSpan.onStart.setStartedAt(220)
        activityTimeSpan.onStart.setStoppedAt(240)
        activityTimeSpan.onCreate.setStoppedAt(260)
        appStartMetrics.addActivityLifecycleTimeSpans(activityTimeSpan)

        // when an activity transaction is created
        val sut = fixture.getSut(enableStarfish = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.hub)
        var tr = SentryTransaction(tracer)

        // and it contains an app start signal
        tr.spans.add(
            SentrySpan(
                0.0,
                1.0,
                tr.contexts.trace!!.traceId,
                SpanId(),
                null,
                APP_START_COLD,
                "App Start",
                SpanStatus.OK,
                null,
                emptyMap(),
                null
            )
        )

        // then the app start metrics should be attached
        tr = sut.process(tr, Hint())

        assertTrue(
            tr.spans.any {
                UI_LOAD_OP == it.op && "Activity" == it.description
            }
        )
        assertTrue(
            tr.spans.any {
                UI_LOAD_OP == it.op && "MainActivity.onCreate" == it.description
            }
        )
        assertTrue(
            tr.spans.any {
                UI_LOAD_OP == it.op && "MainActivity.onStart" == it.description
            }
        )
    }

    private fun setAppStart(options: SentryAndroidOptions, coldStart: Boolean = true) {
        AppStartMetrics.getInstance().apply {
            appStartType = when (coldStart) {
                true -> AppStartType.COLD
                false -> AppStartType.WARM
            }
            val timeSpan =
                if (options.isEnableStarfish) appStartTimeSpan else sdkAppStartTimeSpan
            timeSpan.apply {
                setStartedAt(1)
                setStoppedAt(2)
            }
        }
    }

    private fun getTransaction(type: AppStartType): SentryTransaction {
        val op = when (type) {
            AppStartType.COLD -> "app.start.cold"
            AppStartType.WARM -> "app.start.warm"
            AppStartType.UNKNOWN -> "ui.load"
        }
        val txn = SentryTransaction(fixture.tracer)
        txn.contexts.trace = SpanContext(op, TracesSamplingDecision(false))
        return txn
    }
}
