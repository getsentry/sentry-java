package io.sentry.android.core

import android.content.ContentProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.MeasurementUnit
import io.sentry.SentryTracer
import io.sentry.SpanContext
import io.sentry.SpanDataConvention
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.android.core.ActivityLifecycleIntegration.APP_START_COLD
import io.sentry.android.core.ActivityLifecycleIntegration.APP_START_WARM
import io.sentry.android.core.ActivityLifecycleIntegration.UI_LOAD_OP
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.core.performance.AppStartMetrics.AppStartType
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PerformanceAndroidEventProcessorTest {

    private class Fixture {
        val options = SentryAndroidOptions()

        val scopes = mock<IScopes>()
        val context = TransactionContext("name", "op", TracesSamplingDecision(true))
        lateinit var tracer: SentryTracer
        val activityFramesTracker = mock<ActivityFramesTracker>()

        fun getSut(
            tracesSampleRate: Double? = 1.0,
            enablePerformanceV2: Boolean = false
        ): PerformanceAndroidEventProcessor {
            AppStartMetrics.getInstance().isAppLaunchedInForeground = true
            options.tracesSampleRate = tracesSampleRate
            options.isEnablePerformanceV2 = enablePerformanceV2
            whenever(scopes.options).thenReturn(options)
            tracer = SentryTracer(context, scopes)
            return PerformanceAndroidEventProcessor(options, activityFramesTracker)
        }
    }

    private val fixture = Fixture()

    private fun createAppStartSpan(traceId: SentryId, coldStart: Boolean = true) = SentrySpan(
        0.0,
        1.0,
        traceId,
        SpanId(),
        null,
        if (coldStart) APP_START_COLD else APP_START_WARM,
        "App Start",
        SpanStatus.OK,
        null,
        emptyMap(),
        emptyMap(),
        null
    ).also {
        AppStartMetrics.getInstance().onActivityCreated(mock(), if (coldStart) null else mock())
    }

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
    fun `add cold start measurement for performance-v2`() {
        val sut = fixture.getSut(enablePerformanceV2 = true)

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
        val tracer = SentryTracer(context, fixture.scopes)
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
        appStartMetrics.isAppLaunchedInForeground = true
        appStartMetrics.appStartType = AppStartType.COLD
        appStartMetrics.appStartTimeSpan.setStartedAt(123)
        appStartMetrics.appStartTimeSpan.setStoppedAt(456)

        val contentProvider = mock<ContentProvider>()
        AppStartMetrics.onContentProviderCreate(contentProvider)
        AppStartMetrics.onContentProviderPostCreate(contentProvider)

        appStartMetrics.applicationOnCreateTimeSpan.apply {
            setStartedAt(10)
            setStoppedAt(42)
        }

        val activityTimeSpan = ActivityLifecycleTimeSpan()
        activityTimeSpan.onCreate.description = "MainActivity.onCreate"
        activityTimeSpan.onStart.description = "MainActivity.onStart"

        activityTimeSpan.onCreate.setStartedAt(200)
        activityTimeSpan.onStart.setStartedAt(220)
        activityTimeSpan.onStart.setStoppedAt(240)
        activityTimeSpan.onCreate.setStoppedAt(260)
        appStartMetrics.addActivityLifecycleTimeSpans(activityTimeSpan)

        // when an activity transaction is created
        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)

        // and it contains an app.start.cold span
        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId)
        tr.spans.add(appStartSpan)

        // then the app start metrics should be attached
        tr = sut.process(tr, Hint())

        assertTrue(
            tr.spans.any {
                "process.load" == it.op &&
                    appStartSpan.spanId == it.parentSpanId
            }
        )

        assertTrue(
            tr.spans.any {
                "contentprovider.load" == it.op &&
                    appStartSpan.spanId == it.parentSpanId
            }
        )

        assertTrue(
            tr.spans.any {
                "application.load" == it.op
            }
        )
    }

    @Test
    fun `does not add app start metrics to app warm start txn`() {
        // given some app start metrics
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.WARM
        appStartMetrics.appStartTimeSpan.setStartedAt(123)
        appStartMetrics.appStartTimeSpan.setStoppedAt(456)

        val contentProvider = mock<ContentProvider>()
        AppStartMetrics.onContentProviderCreate(contentProvider)
        AppStartMetrics.onContentProviderPostCreate(contentProvider)

        appStartMetrics.applicationOnCreateTimeSpan.apply {
            setStartedAt(10)
            setStoppedAt(42)
        }

        val activityTimeSpan = ActivityLifecycleTimeSpan()
        activityTimeSpan.onCreate.description = "MainActivity.onCreate"
        activityTimeSpan.onStart.description = "MainActivity.onStart"

        activityTimeSpan.onCreate.setStartedAt(200)
        activityTimeSpan.onStart.setStartedAt(220)
        activityTimeSpan.onStart.setStoppedAt(240)
        activityTimeSpan.onCreate.setStoppedAt(260)
        appStartMetrics.addActivityLifecycleTimeSpans(activityTimeSpan)

        // when an activity transaction is created
        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)

        // and it contains an app.start.warm span
        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId, false)
        tr.spans.add(appStartSpan)

        // then the app start metrics should be attached
        tr = sut.process(tr, Hint())

        // process init, content provider and application span should not be attached
        assertFalse(tr.spans.any { "process.load" == it.op })
        assertFalse(tr.spans.any { "contentprovider.load" == it.op })
        assertFalse(tr.spans.any { "application.load" == it.op })
    }

    @Test
    fun `when app launched from background, app start spans are dropped`() {
        // given some app start metrics
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.COLD
        appStartMetrics.appStartTimeSpan.setStartedAt(123)
        appStartMetrics.appStartTimeSpan.setStoppedAt(456)

        val contentProvider = mock<ContentProvider>()
        AppStartMetrics.onContentProviderCreate(contentProvider)
        AppStartMetrics.onContentProviderPostCreate(contentProvider)

        appStartMetrics.applicationOnCreateTimeSpan.apply {
            setStartedAt(10)
            setStoppedAt(42)
        }

        val activityTimeSpan = ActivityLifecycleTimeSpan()
        activityTimeSpan.onCreate.description = "MainActivity.onCreate"
        activityTimeSpan.onStart.description = "MainActivity.onStart"

        activityTimeSpan.onCreate.setStartedAt(200)
        activityTimeSpan.onStart.setStartedAt(220)
        activityTimeSpan.onStart.setStoppedAt(240)
        activityTimeSpan.onCreate.setStoppedAt(260)
        appStartMetrics.addActivityLifecycleTimeSpans(activityTimeSpan)

        // when an activity transaction is created
        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)

        // and it contains an app.start.cold span
        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId)
        tr.spans.add(appStartSpan)

        // but app is launched in background
        AppStartMetrics.getInstance().isAppLaunchedInForeground = false

        // then the app start metrics are not attached
        tr = sut.process(tr, Hint())

        assertFalse(
            tr.spans.any {
                "process.load" == it.op ||
                    "contentprovider.load" == it.op ||
                    "application.load" == it.op ||
                    "activity.load" == it.op
            }
        )
    }

    @Test
    fun `when app start takes more than 1 minute, app start spans are dropped`() {
        // given some app start metrics
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.COLD
        appStartMetrics.appStartTimeSpan.setStartedAt(123)
        // and app start takes more than 1 minute
        appStartMetrics.appStartTimeSpan.setStoppedAt(TimeUnit.MINUTES.toMillis(1) + 124)

        val contentProvider = mock<ContentProvider>()
        AppStartMetrics.onContentProviderCreate(contentProvider)
        AppStartMetrics.onContentProviderPostCreate(contentProvider)

        appStartMetrics.applicationOnCreateTimeSpan.apply {
            setStartedAt(10)
            setStoppedAt(42)
        }

        val activityTimeSpan = ActivityLifecycleTimeSpan()
        activityTimeSpan.onCreate.description = "MainActivity.onCreate"
        activityTimeSpan.onStart.description = "MainActivity.onStart"

        activityTimeSpan.onCreate.setStartedAt(200)
        activityTimeSpan.onStart.setStartedAt(220)
        activityTimeSpan.onStart.setStoppedAt(240)
        activityTimeSpan.onCreate.setStoppedAt(260)
        appStartMetrics.addActivityLifecycleTimeSpans(activityTimeSpan)

        // when an activity transaction is created
        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)

        // and it contains an app.start.cold span
        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId)
        tr.spans.add(appStartSpan)

        // then the app start metrics are not attached
        tr = sut.process(tr, Hint())

        assertFalse(
            tr.spans.any {
                "process.load" == it.op ||
                    "contentprovider.load" == it.op ||
                    "application.load" == it.op ||
                    "activity.load" == it.op
            }
        )
    }

    @Test
    fun `does not add app start metrics to app start txn when it is not a cold start`() {
        // given some WARM app start metrics
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.WARM
        appStartMetrics.appStartTimeSpan.setStartedAt(123)
        appStartMetrics.appStartTimeSpan.setStoppedAt(456)
        appStartMetrics.applicationOnCreateTimeSpan.apply {
            setStartedAt(10)
            setStoppedAt(42)
        }
        // when an activity transaction is created
        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)

        // then the app start metrics should not be attached
        tr = sut.process(tr, Hint())

        assertFalse(
            tr.spans.any {
                "application.load" == it.op
            }
        )
    }

    @Test
    fun `does not add app start metrics more than once`() {
        // given some cold app start metrics
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.COLD
        appStartMetrics.appStartTimeSpan.setStartedAt(123)
        appStartMetrics.appStartTimeSpan.setStoppedAt(456)

        appStartMetrics.applicationOnCreateTimeSpan.apply {
            setStartedAt(10)
            setStoppedAt(42)
        }

        // when the first activity transaction is created
        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)
        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId)
        tr.spans.add(appStartSpan)

        assertTrue(appStartMetrics.shouldSendStartMeasurements())
        // then the app start metrics should be attached
        tr = sut.process(tr, Hint())
        assertFalse(appStartMetrics.shouldSendStartMeasurements())

        assertTrue(
            tr.spans.any {
                "application.load" == it.op
            }
        )

        // but not on the second activity transaction
        var tr2 = SentryTransaction(tracer)
        tr2.spans.add(appStartSpan)
        tr2 = sut.process(tr2, Hint())
        assertFalse(
            tr2.spans.any {
                "application.load" == it.op
            }
        )
    }

    @Test
    fun `does not add process init span if it happened too early`() {
        // given some cold app start metrics
        // where class loaded happened way before app start
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.COLD
        appStartMetrics.appStartTimeSpan.setStartedAt(11001)
        appStartMetrics.appStartTimeSpan.setStoppedAt(12000)
        appStartMetrics.classLoadedUptimeMs = 1000

        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)
        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId)
        tr.spans.add(appStartSpan)

        // when the processor attaches the app start spans
        tr = sut.process(tr, Hint())

        // process load should not be included
        assertFalse(
            tr.spans.any {
                "process.load" == it.op
            }
        )
    }

    @Test
    fun `adds main thread name and id to app start spans`() {
        // given some cold app start metrics
        // where class loaded happened way before app start
        val appStartMetrics = AppStartMetrics.getInstance()
        appStartMetrics.appStartType = AppStartType.COLD
        appStartMetrics.appStartTimeSpan.setStartedAt(1)
        appStartMetrics.appStartTimeSpan.setStoppedAt(3000)

        AppStartMetrics.getInstance().applicationOnCreateTimeSpan.apply {
            setStartedAt(1000)
            description = "com.example.App.onCreate"
            setStoppedAt(2000)
        }

        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)
        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId)
        tr.spans.add(appStartSpan)

        // when the processor attaches the app start spans
        tr = sut.process(tr, Hint())

        // thread name and id should be set
        assertTrue {
            tr.spans.any {
                it.op == "process.load" &&
                    it.data!!["thread.name"] == "main" &&
                    it.data!!.containsKey("thread.id")
            }
        }

        assertTrue {
            tr.spans.any {
                it.op == "application.load" &&
                    it.data!!["thread.name"] == "main" &&
                    it.data!!.containsKey("thread.id")
            }
        }
    }

    @Test
    fun `does not set start_type field for txns without app start span`() {
        // given some ui.load txn
        setAppStart(fixture.options, coldStart = true)

        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)

        // when it contains no app start span and is processed
        tr = sut.process(tr, Hint())

        // start_type should not be set
        assertNull(tr.contexts.app?.startType)
    }

    @Test
    fun `sets start_type field for app context`() {
        // given some cold app start
        setAppStart(fixture.options, coldStart = true)

        val sut = fixture.getSut(enablePerformanceV2 = true)
        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        var tr = SentryTransaction(tracer)

        val appStartSpan = createAppStartSpan(tr.contexts.trace!!.traceId)
        tr.spans.add(appStartSpan)

        // when the processor attaches the app start spans
        tr = sut.process(tr, Hint())

        // start_type should be set as well
        assertEquals(
            "cold",
            tr.contexts.app!!.startType
        )
    }

    @Test
    fun `adds ttid and ttfd contributing span data`() {
        val sut = fixture.getSut()

        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        val tr = SentryTransaction(tracer)

        // given a ttid from 0.0 -> 1.0
        //   and a ttfd from 0.0 -> 2.0
        val ttid = SentrySpan(
            0.0,
            1.0,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            ActivityLifecycleIntegration.TTID_OP,
            "App Start",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )

        val ttfd = SentrySpan(
            0.0,
            2.0,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            ActivityLifecycleIntegration.TTFD_OP,
            "App Start",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )
        tr.spans.add(ttid)
        tr.spans.add(ttfd)

        // and 3 spans
        // one from 0.0 -> 0.5
        val ttidContrib = SentrySpan(
            0.0,
            0.5,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            "example.op",
            "",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )

        // and another from 1.5 -> 3.5
        val ttfdContrib = SentrySpan(
            1.5,
            3.5,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            "example.op",
            "",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )

        // and another from 2.1 -> 2.2
        val outsideSpan = SentrySpan(
            2.1,
            2.2,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            "example.op",
            "",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            mutableMapOf<String, Any>(
                "tag" to "value"
            )
        )

        tr.spans.add(ttidContrib)
        tr.spans.add(ttfdContrib)

        // when the processor processes the txn
        sut.process(tr, Hint())

        // then the ttid/ttfd spans themselves should have no flags set
        assertNull(ttid.data?.get(SpanDataConvention.CONTRIBUTES_TTID))
        assertNull(ttid.data?.get(SpanDataConvention.CONTRIBUTES_TTFD))

        assertNull(ttfd.data?.get(SpanDataConvention.CONTRIBUTES_TTID))
        assertNull(ttfd.data?.get(SpanDataConvention.CONTRIBUTES_TTFD))

        // then the first span should have ttid and ttfd contributing flags
        assertTrue(ttidContrib.data?.get(SpanDataConvention.CONTRIBUTES_TTID) == true)
        assertTrue(ttidContrib.data?.get(SpanDataConvention.CONTRIBUTES_TTFD) == true)

        // and the second one should contribute to ttfd only
        assertNull(ttfdContrib.data?.get(SpanDataConvention.CONTRIBUTES_TTID))
        assertTrue(ttfdContrib.data?.get(SpanDataConvention.CONTRIBUTES_TTFD) == true)

        // and the third span should have no flags attached, as it's outside ttid/ttfd
        assertNull(outsideSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTID))
        assertNull(outsideSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTFD))
    }

    @Test
    fun `adds no ttid and ttfd contributing span data if txn contains no ttid or ttfd`() {
        val sut = fixture.getSut()

        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        val tr = SentryTransaction(tracer)

        val span = SentrySpan(
            0.0,
            1.0,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            "example.op",
            "",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )

        tr.spans.add(span)

        // when the processor processes the txn
        sut.process(tr, Hint())

        // the span should have no flags attached
        assertNull(span.data?.get(SpanDataConvention.CONTRIBUTES_TTID))
        assertNull(span.data?.get(SpanDataConvention.CONTRIBUTES_TTFD))
    }

    @Test
    fun `sets ttid and ttfd contributing flags according to span threads`() {
        val sut = fixture.getSut()

        val context = TransactionContext("Activity", UI_LOAD_OP)
        val tracer = SentryTracer(context, fixture.scopes)
        val tr = SentryTransaction(tracer)

        // given a ttid from 0.0 -> 1.0
        //   and a ttfd from 0.0 -> 1.0
        val ttid = SentrySpan(
            0.0,
            1.0,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            ActivityLifecycleIntegration.TTID_OP,
            "App Start",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )

        val ttfd = SentrySpan(
            0.0,
            1.0,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            ActivityLifecycleIntegration.TTFD_OP,
            "App Start",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )
        tr.spans.add(ttid)
        tr.spans.add(ttfd)

        // one span with no thread info
        val noThreadSpan = SentrySpan(
            0.0,
            0.5,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            "example.op",
            "",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            null
        )

        // one span on the main thread
        val mainThreadSpan = SentrySpan(
            0.0,
            0.5,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            "example.op",
            "",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            mutableMapOf<String, Any>(
                "thread.name" to "main"
            )
        )

        // and another one off the main thread
        val backgroundThreadSpan = SentrySpan(
            0.0,
            0.5,
            tr.contexts.trace!!.traceId,
            SpanId(),
            null,
            "example.op",
            "",
            SpanStatus.OK,
            null,
            emptyMap(),
            emptyMap(),
            mutableMapOf<String, Any>(
                "thread.name" to "background"
            )
        )

        tr.spans.add(noThreadSpan)
        tr.spans.add(mainThreadSpan)
        tr.spans.add(backgroundThreadSpan)

        // when the processor processes the txn
        sut.process(tr, Hint())

        // then the span with no thread info + main thread span should contribute to ttid and ttfd
        assertTrue(noThreadSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTID) == true)
        assertTrue(noThreadSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTFD) == true)

        assertTrue(mainThreadSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTID) == true)
        assertTrue(mainThreadSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTFD) == true)

        // and the background thread span only contributes to ttfd
        assertNull(backgroundThreadSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTID))
        assertTrue(backgroundThreadSpan.data?.get(SpanDataConvention.CONTRIBUTES_TTFD) == true)
    }

    private fun setAppStart(options: SentryAndroidOptions, coldStart: Boolean = true) {
        AppStartMetrics.getInstance().apply {
            appStartType = when (coldStart) {
                true -> AppStartType.COLD
                false -> AppStartType.WARM
            }
            val timeSpan =
                if (options.isEnablePerformanceV2) appStartTimeSpan else sdkInitTimeSpan
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
        txn.contexts.setTrace(SpanContext(op, TracesSamplingDecision(false)))
        return txn
    }
}
