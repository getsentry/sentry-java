package io.sentry.android.core

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.os.Bundle
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.FullDisplayedReporter
import io.sentry.Hub
import io.sentry.ISentryExecutorService
import io.sentry.Scope
import io.sentry.SentryDate
import io.sentry.SentryLevel
import io.sentry.SentryNanotimeDate
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TraceContext
import io.sentry.TransactionContext
import io.sentry.TransactionFinishedCallback
import io.sentry.TransactionOptions
import io.sentry.protocol.TransactionNameSource
import io.sentry.test.getProperty
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ActivityLifecycleIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val am = mock<ActivityManager>()
        val hub = mock<Hub>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val bundle = mock<Bundle>()
        val context = TransactionContext("name", "op")
        val activityFramesTracker = mock<ActivityFramesTracker>()
        val fullDisplayedReporter = FullDisplayedReporter.getInstance()
        val transactionFinishedCallback = mock<TransactionFinishedCallback>()
        lateinit var transaction: SentryTracer
        val buildInfo = mock<BuildInfoProvider>()

        fun getSut(apiVersion: Int = 29, importance: Int = RunningAppProcessInfo.IMPORTANCE_FOREGROUND): ActivityLifecycleIntegration {
            whenever(hub.options).thenReturn(options)
            transaction = SentryTracer(context, hub, true, transactionFinishedCallback)
            whenever(hub.startTransaction(any(), any<TransactionOptions>())).thenReturn(transaction)
            whenever(buildInfo.sdkInfoVersion).thenReturn(apiVersion)

            whenever(application.getSystemService(any())).thenReturn(am)

            val process = RunningAppProcessInfo().apply {
                this.importance = importance
            }
            val processes = mutableListOf(process)

            whenever(am.runningAppProcesses).thenReturn(processes)

            return ActivityLifecycleIntegration(application, buildInfo, activityFramesTracker)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `reset instance`() {
        AppStartState.getInstance().resetInstance()
    }

    @Test
    fun `When activity lifecycle breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When activity lifecycle breadcrumb and tracing are disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        fixture.options.isEnableActivityLifecycleBreadcrumbs = false

        sut.register(fixture.hub, fixture.options)

        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When activity lifecycle breadcrumb is disabled but tracing sample rate is enabled, it registers callback`() {
        val sut = fixture.getSut()
        fixture.options.isEnableActivityLifecycleBreadcrumbs = false
        fixture.options.tracesSampleRate = 1.0

        sut.register(fixture.hub, fixture.options)

        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When activity lifecycle breadcrumb is disabled but tracing sample callback is enabled, it registers callback`() {
        val sut = fixture.getSut()
        fixture.options.isEnableActivityLifecycleBreadcrumbs = false
        fixture.options.tracesSampler = SentryOptions.TracesSamplerCallback { 1.0 }

        sut.register(fixture.hub, fixture.options)

        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When activity lifecycle breadcrumb and tracing activity flag are disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        fixture.options.isEnableActivityLifecycleBreadcrumbs = false
        fixture.options.tracesSampleRate = 1.0
        fixture.options.tracesSampler = SentryOptions.TracesSamplerCallback { 1.0 }
        fixture.options.isEnableAutoActivityLifecycleTracing = false

        sut.register(fixture.hub, fixture.options)

        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.close()

        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should close the ActivityFramesTracker`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.close()

        verify(fixture.activityFramesTracker).stop()
    }

    @Test
    fun `When breadcrumb is added, type and category should be set`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("ui.lifecycle", it.category)
                assertEquals("navigation", it.type)
                assertEquals(SentryLevel.INFO, it.level)
                // cant assert data, its not a public API
            },
            anyOrNull()
        )
    }

    @Test
    fun `When activity is created, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is started, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityStarted(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is resumed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityResumed(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is paused, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPaused(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is stopped, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityStopped(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is save instance, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivitySaveInstanceState(activity, fixture.bundle)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is destroyed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityDestroyed(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When tracing is disabled, do not start tracing`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub, never()).startTransaction(any(), any<TransactionOptions>())
    }

    @Test
    fun `When tracing is enabled but activity is running, do not start tracing again`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).startTransaction(any(), any<TransactionOptions>())
    }

    @Test
    fun `Transaction op is ui_load`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("ui.load", it.operation)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )
    }

    @Test
    fun `Activity gets added to ActivityFramesTracker during transaction creation`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityStarted(activity)

        verify(fixture.activityFramesTracker).addActivity(eq(activity))
    }

    @Test
    fun `Transaction name is the Activity's name`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).startTransaction(
            check {
                assertEquals("Activity", it.name)
                assertEquals(TransactionNameSource.COMPONENT, it.transactionNameSource)
            },
            any<TransactionOptions>()
        )
    }

    @Test
    fun `When transaction is created, set transaction to the bound Scope`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0

        sut.register(fixture.hub, fixture.options)

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)

            sut.applyScope(scope, fixture.transaction)

            assertNotNull(scope.transaction)
        }

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
    }

    @Test
    fun `When transaction is created, do not overwrite transaction already bound to the Scope`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0

        sut.register(fixture.hub, fixture.options)

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)
            val previousTransaction = SentryTracer(TransactionContext("name", "op"), fixture.hub)
            scope.transaction = previousTransaction

            sut.applyScope(scope, fixture.transaction)

            assertEquals(previousTransaction, scope.transaction)
        }

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
    }

    @Test
    fun `When tracing auto finish is enabled and ttid and ttfd spans are finished, it stops the transaction on onActivityPostResumed`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.ttidSpanMap.values.first().finish()
        sut.ttfdSpan?.finish()
        sut.onActivityPostResumed(activity)

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(SpanStatus.OK, it.status)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `When tracing auto finish is enabled, it doesn't stop the transaction on onActivityPostResumed`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityPostResumed(activity)

        verify(fixture.hub, never()).captureTransaction(
            check {
                assertEquals(SpanStatus.OK, it.status)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `When tracing has status, do not overwrite it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        fixture.transaction.status = SpanStatus.UNKNOWN_ERROR

        sut.onActivityPostResumed(activity)
        sut.onActivityDestroyed(activity)

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(SpanStatus.UNKNOWN_ERROR, it.status)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `When tracing auto finish is disabled, do not finish transaction`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableActivityLifecycleTracingAutoFinish = false
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityPostResumed(activity)

        verify(fixture.hub, never()).captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `When tracing is disabled, do not finish transaction`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPostResumed(activity)

        verify(fixture.hub, never()).captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `When Activity is destroyed but transaction is running, finish it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        verify(fixture.hub).captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `When transaction is started, adds to WeakWef`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        assertFalse(sut.activitiesWithOngoingTransactions.isEmpty())
    }

    @Test
    fun `When Activity is destroyed removes WeakRef`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        assertTrue(sut.activitiesWithOngoingTransactions.isEmpty())
    }

    @Test
    fun `When Activity is destroyed, sets appStartSpan status to cancelled and finish it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        val span = fixture.transaction.children.first()
        assertEquals(span.status, SpanStatus.CANCELLED)
        assertTrue(span.isFinished)
    }

    @Test
    fun `When Activity is destroyed, sets appStartSpan to null`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        assertNull(sut.appStartSpan)
    }

    @Test
    fun `When Activity is destroyed, sets ttidSpan status to deadline_exceeded and finish it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        val span = fixture.transaction.children.first { it.operation == ActivityLifecycleIntegration.TTID_OP }
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, span.status)
        assertTrue(span.isFinished)
    }

    @Test
    fun `When Activity is destroyed, sets ttidSpan to null`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        assertNotNull(sut.ttidSpanMap[activity])

        sut.onActivityDestroyed(activity)
        assertNull(sut.ttidSpanMap[activity])
    }

    @Test
    fun `When Activity is destroyed, sets ttfdSpan status to deadline_exceeded and finish it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        val span = fixture.transaction.children.first { it.operation == ActivityLifecycleIntegration.TTFD_OP }
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, span.status)
        assertTrue(span.isFinished)
    }

    @Test
    fun `When Activity is destroyed, sets ttfdSpan to null`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        assertNotNull(sut.ttfdSpan)

        sut.onActivityDestroyed(activity)
        assertNull(sut.ttfdSpan)
    }

    @Test
    fun `When new Activity and transaction is created, finish previous ones`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityCreated(mock(), mock())

        sut.onActivityCreated(mock(), fixture.bundle)
        verify(fixture.hub).captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `do not stop transaction on resumed if API 29`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, mock())
        sut.onActivityResumed(activity)

        verify(fixture.hub, never()).captureTransaction(any(), any<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `start transaction on created if API less than 29`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, mock())

        verify(fixture.hub).startTransaction(any(), any<TransactionOptions>())
    }

    @Test
    fun `stop transaction on resumed if API 29 less than 29 and ttid and ttfd are finished`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, mock())
        sut.ttidSpanMap.values.first().finish()
        sut.ttfdSpan?.finish()
        sut.onActivityResumed(activity)

        verify(fixture.hub).captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `reportFullyDrawn finishes the ttfd`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, mock())
        sut.ttidSpanMap.values.first().finish()
        fixture.fullDisplayedReporter.reportFullyDrawn()
        assertTrue(sut.ttfdSpan!!.isFinished)
        assertNotEquals(SpanStatus.CANCELLED, sut.ttfdSpan?.status)
    }

    @Test
    fun `App start is Cold when savedInstanceState is null`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, null)

        assertTrue(AppStartState.getInstance().isColdStart!!)
    }

    @Test
    fun `App start is Warm when savedInstanceState is not null`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        val bundle = Bundle()
        sut.onActivityCreated(activity, bundle)

        assertFalse(AppStartState.getInstance().isColdStart!!)
    }

    @Test
    fun `Do not overwrite App start type after set`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        val bundle = Bundle()
        sut.onActivityCreated(activity, bundle)
        sut.onActivityCreated(activity, null)

        assertFalse(AppStartState.getInstance().isColdStart!!)
    }

    @Test
    fun `When firstActivityCreated is true, start transaction with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        // call only once
        verify(fixture.hub).startTransaction(any(), check<TransactionOptions> { assertEquals(date, it.startTimestamp) })
    }

    @Test
    fun `When firstActivityCreated is true, do not create app start span if not foregroundImportance`() {
        val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_BACKGROUND)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        // usually set by SentryPerformanceProvider
        val date = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(date)
        AppStartState.getInstance().setAppStartEnd(1)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        // call only once
        verify(fixture.hub).startTransaction(any(), check<TransactionOptions> { assertNull(it.startTimestamp) })
    }

    @Test
    fun `Create and finish app start span immediately in case SDK init is deferred`() {
        val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        // usually set by SentryPerformanceProvider
        val startDate = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(startDate)
        AppStartState.getInstance().setColdStart(false)
        AppStartState.getInstance().setAppStartEnd(1)

        val endDate = AppStartState.getInstance().appStartEndTime!!

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        val appStartSpanCount = fixture.transaction.children.count {
            it.spanContext.operation.startsWith("app.start.warm") &&
                it.startDate.nanoTimestamp() == startDate.nanoTimestamp() &&
                it.finishDate!!.nanoTimestamp() == endDate.nanoTimestamp()
        }
        assertEquals(1, appStartSpanCount)
    }

    @Test
    fun `When SentryPerformanceProvider is disabled, app start time span is still created`() {
        val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        // usually done by SentryPerformanceProvider, if disabled it's done by
        // SentryAndroid.init
        val startDate = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(startDate)
        AppStartState.getInstance().setColdStart(false)

        // when activity is created
        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        // then app-start end time should still be null
        assertNull(AppStartState.getInstance().appStartEndTime)

        // when activity is resumed
        sut.onActivityResumed(activity)
        // end-time should be set
        assertNotNull(AppStartState.getInstance().appStartEndTime)
    }

    @Test
    fun `When app-start end time is already set, it should not be overwritten`() {
        val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        // usually done by SentryPerformanceProvider
        val startDate = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(startDate)
        AppStartState.getInstance().setColdStart(false)
        AppStartState.getInstance().setAppStartEnd(1234)

        // when activity is created and resumed
        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityResumed(activity)

        // then the end time should not be overwritten
        assertEquals(
            DateUtils.millisToNanos(1234),
            AppStartState.getInstance().appStartEndTime!!.nanoTimestamp()
        )
    }

    @Test
    fun `When activity lifecycle happens multiple times, app-start end time should not be overwritten`() {
        val sut = fixture.getSut(importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        // usually done by SentryPerformanceProvider
        val startDate = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(startDate)
        AppStartState.getInstance().setColdStart(false)

        // when activity is created, started and resumed multiple times
        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityStarted(activity)
        sut.onActivityResumed(activity)

        val firstAppStartEndTime = AppStartState.getInstance().appStartEndTime

        Thread.sleep(1)
        sut.onActivityPaused(activity)
        sut.onActivityStopped(activity)
        sut.onActivityStarted(activity)
        sut.onActivityResumed(activity)

        // then the end time should not be overwritten
        assertEquals(
            firstAppStartEndTime!!.nanoTimestamp(),
            AppStartState.getInstance().appStartEndTime!!.nanoTimestamp()
        )
    }

    @Test
    fun `When firstActivityCreated is true, start app start warm span with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        val span = fixture.transaction.children.first()
        assertEquals(span.operation, "app.start.warm")
        assertSame(span.startDate, date)
    }

    @Test
    fun `When firstActivityCreated is true, start app start cold span with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, null)

        val span = fixture.transaction.children.first()
        assertEquals(span.operation, "app.start.cold")
        assertSame(span.startDate, date)
    }

    @Test
    fun `When firstActivityCreated is true, start app start span with Warm description`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        val span = fixture.transaction.children.first()
        assertEquals(span.description, "Warm Start")
        assertSame(span.startDate, date)
    }

    @Test
    fun `When firstActivityCreated is true, start app start span with Cold description`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = SentryNanotimeDate(Date(0), 0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, null)

        val span = fixture.transaction.children.first()
        assertEquals(span.description, "Cold Start")
        assertSame(span.startDate, date)
    }

    @Test
    fun `When firstActivityCreated is false, start transaction but not with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = SentryNanotimeDate(Date(0), 0)
        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).startTransaction(
            any(),
            check<TransactionOptions> {
                assertEquals(date.nanoTimestamp(), it.startTimestamp!!.nanoTimestamp())
            }
        )
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityPostResumed(activity)

        val newActivity = mock<Activity>()
        sut.onActivityCreated(newActivity, fixture.bundle)

        val nullDate: Date? = null
        verify(fixture.hub).startTransaction(any(), check<TransactionOptions> { assertNull(it.startTimestamp) })
    }

    @Test
    fun `When transaction is finished, it gets removed from scope`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)

            scope.transaction = fixture.transaction

            sut.clearScope(scope, fixture.transaction)

            assertNull(scope.transaction)
        }

        sut.onActivityDestroyed(activity)
    }

    @Test
    fun `When transaction is started and isEnableTimeToFullDisplayTracing is disabled, no ttfd span is started`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = false
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        assertNull(sut.ttfdSpan)
    }

    @Test
    fun `When transaction is started and isEnableTimeToFullDisplayTracing is enabled, ttfd span is started`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        assertNotNull(sut.ttfdSpan)
    }

    @Test
    fun `When isEnableTimeToFullDisplayTracing is true and reportFullyDrawn is not called, ttfd span is finished automatically with timeout`() {
        val sut = fixture.getSut()
        var lastScheduledRunnable: Runnable? = null
        val mockExecutorService = object : ISentryExecutorService {
            override fun submit(runnable: Runnable): Future<*> = mock()
            override fun <T> submit(callable: Callable<T>): Future<T> = mock()
            override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
                lastScheduledRunnable = runnable
                return FutureTask {}
            }
            override fun close(timeoutMillis: Long) {}
        }
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        fixture.options.executorService = mockExecutorService
        sut.register(fixture.hub, fixture.options)
        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        val ttfdSpan = sut.ttfdSpan

        // Assert the ttfd span is running and a timeout autoCancel task has been scheduled
        assertNotNull(ttfdSpan)
        assertFalse(ttfdSpan.isFinished)
        assertNotNull(lastScheduledRunnable)

        // Run the autoClose task and assert the ttfd span is finished with deadlineExceeded
        lastScheduledRunnable!!.run()
        assertTrue(ttfdSpan.isFinished)
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, ttfdSpan.status)
    }

    @Test
    fun `When isEnableTimeToFullDisplayTracing is true and reportFullyDrawn is called, ttfd autoClose future is cancelled`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)
        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        val ttfdSpan = sut.ttfdSpan
        var autoCloseFuture = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")

        // Assert the ttfd span is running and a timeout autoCancel future has been scheduled
        assertNotNull(ttfdSpan)
        assertFalse(ttfdSpan.isFinished)
        assertNotNull(autoCloseFuture)

        // ReportFullyDrawn should finish the ttfd span and cancel the future
        fixture.options.fullDisplayedReporter.reportFullyDrawn()
        assertTrue(ttfdSpan.isFinished)
        assertNotEquals(SpanStatus.DEADLINE_EXCEEDED, ttfdSpan.status)
        assertTrue(autoCloseFuture.isCancelled)

        // The current internal reference to autoClose future should be null after ReportFullyDrawn
        autoCloseFuture = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")
        assertNull(autoCloseFuture)
    }

    @Test
    fun `When isEnableTimeToFullDisplayTracing is true and another activity starts, the old ttfd is finished and the old autoClose future is cancelled`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableTimeToFullDisplayTracing = true
        sut.register(fixture.hub, fixture.options)
        val activity = mock<Activity>()
        val activity2 = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)
        val ttfdSpan = sut.ttfdSpan
        val autoCloseFuture = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")

        // Assert the ttfd span is running and a timeout autoCancel future has been scheduled
        assertNotNull(ttfdSpan)
        assertFalse(ttfdSpan.isFinished)
        assertNotNull(autoCloseFuture)

        // Starting a new Activity should finish the old ttfd span with deadlineExceeded and cancel the old future
        sut.onActivityCreated(activity2, fixture.bundle)
        assertTrue(ttfdSpan.isFinished)
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, ttfdSpan.status)
        assertTrue(autoCloseFuture.isCancelled)

        // Another autoClose future and ttfd span should be started after the second activity starts
        val autoCloseFuture2 = sut.getProperty<Future<*>?>("ttfdAutoCloseFuture")
        val ttfdSpan2 = sut.ttfdSpan
        assertNotNull(ttfdSpan2)
        assertFalse(ttfdSpan2.isFinished)
        assertNotNull(autoCloseFuture2)
        assertFalse(autoCloseFuture2.isCancelled)
    }

    private fun setAppStartTime(date: SentryDate = SentryNanotimeDate(Date(0), 0)) {
        // set by SentryPerformanceProvider so forcing it here
        AppStartState.getInstance().setAppStartTime(0, date)
    }
}
