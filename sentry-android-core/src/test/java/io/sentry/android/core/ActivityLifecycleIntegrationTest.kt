package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.Hub
import io.sentry.Scope
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ActivityLifecycleIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val hub = mock<Hub>()
        val options = SentryAndroidOptions()
        val bundle = mock<Bundle>()
        val context = TransactionContext("name", "op")
        val transaction = SentryTracer(context, hub)
        val buildInfo = mock<IBuildInfoProvider>()

        fun getSut(apiVersion: Int = 29): ActivityLifecycleIntegration {
            whenever(hub.startTransaction(any<String>(), any())).thenReturn(transaction)
            whenever(hub.options).thenReturn(options)
            whenever(hub.startTransaction(any(), any(), any<Date>(), any())).thenReturn(transaction)
            whenever(buildInfo.sdkInfoVersion).thenReturn(apiVersion)
            return ActivityLifecycleIntegration(application, buildInfo)
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
    fun `When breadcrumb is added, type and category should be set`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("ui.lifecycle", it.category)
            assertEquals("navigation", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When activity is created, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is started, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityStarted(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is resumed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityResumed(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is paused, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPaused(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is stopped, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityStopped(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is save instance, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivitySaveInstanceState(activity, fixture.bundle)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is destroyed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityDestroyed(activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When tracing is disabled, do not start tracing`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        verify(fixture.hub, never()).startTransaction(any<String>(), any())
        verify(fixture.hub, never()).startTransaction(any(), any(), any<Date>(), any())
    }

    @Test
    fun `When tracing is enabled but activity is running, do not start tracing again`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)
        sut.onActivityPreCreated(activity, fixture.bundle)

        // call only once
//        verify(fixture.hub).startTransaction(any<String>(), any())
        verify(fixture.hub, never()).startTransaction(any(), any(), any<Date>(), any())
    }

    @Test
    fun `Transaction op is ui_load`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        verify(fixture.hub).startTransaction(any(), check {
            assertEquals("ui.load", it)
        }, any<Date>(), any())
    }

    @Test
    fun `Transaction name is the Activity's name`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        verify(fixture.hub).startTransaction(check {
            assertEquals("Activity", it)
        }, any(), any<Date>(), any())
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
        sut.onActivityPreCreated(activity, fixture.bundle)
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
        sut.onActivityPreCreated(activity, fixture.bundle)
    }

    @Test
    fun `When tracing auto finish is enabled, it stops the transaction on onActivityPostResumed`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)
        sut.onActivityPostResumed(activity)

        verify(fixture.hub).captureTransaction(check {
            assertEquals(SpanStatus.OK, it.status)
        })
    }

    @Test
    fun `When tracing has status, do not overwrite it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        fixture.transaction.status = SpanStatus.UNKNOWN_ERROR

        sut.onActivityPostResumed(activity)

        verify(fixture.hub).captureTransaction(check {
            assertEquals(SpanStatus.UNKNOWN_ERROR, it.status)
        })
    }

    @Test
    fun `When tracing auto finish is disabled, do not finish transaction`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableActivityLifecycleTracingAutoFinish = false
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)
        sut.onActivityPostResumed(activity)

        verify(fixture.hub, never()).captureTransaction(any())
    }

    @Test
    fun `When tracing is disabled, do not finish transaction`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPostResumed(activity)

        verify(fixture.hub, never()).captureTransaction(any())
    }

    @Test
    fun `When Activity is destroyed but transaction is running, finish it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        verify(fixture.hub).captureTransaction(any())
    }

    @Test
    fun `When transaction is started, adds to WeakWef`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        assertFalse(sut.activitiesWithOngoingTransactions.isEmpty())
    }

    @Test
    fun `When Activity is destroyed removes WeakRef`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)
        sut.onActivityDestroyed(activity)

        assertTrue(sut.activitiesWithOngoingTransactions.isEmpty())
    }

    @Test
    fun `When new Activity and transaction is created, finish previous ones`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(mock(), mock())

        sut.onActivityPreCreated(mock(), fixture.bundle)
        verify(fixture.hub).captureTransaction(any())
    }

    @Test
    fun `do not start transaction on created if API 29`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, mock())

        verify(fixture.hub, never()).startTransaction(any<String>(), any())
        verify(fixture.hub, never()).startTransaction(any(), any(), any<Date>(), any())
    }

    @Test
    fun `do not stop transaction on resumed if API 29`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, mock())
        sut.onActivityResumed(activity)

        verify(fixture.hub, never()).captureTransaction(any())
    }

    @Test
    fun `start transaction on created if API less than 29`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, mock())

        verify(fixture.hub).startTransaction(any(), any(), any<Date>(), any())
    }

    @Test
    fun `stop transaction on resumed if API 29 less than 29`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, mock())
        sut.onActivityResumed(activity)

        verify(fixture.hub).captureTransaction(any())
    }

    @Test
    fun `App start is Cold when savedInstanceState is null`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, null)

        assertTrue(AppStartState.getInstance().isColdStart)
    }

    @Test
    fun `App start is Warm when savedInstanceState is not null`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        val bundle = Bundle()
        sut.onActivityCreated(activity, bundle)

        assertFalse(AppStartState.getInstance().isColdStart)
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

        assertFalse(AppStartState.getInstance().isColdStart)
    }

    @Test
    fun `App start end time is set`() {
        val sut = fixture.getSut(14)
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, null)
        sut.onActivityResumed(activity)

        // SystemClock.uptimeMillis() always returns 0, can't assert real values
        assertNotNull(AppStartState.getInstance().appStartInterval)
    }

    @Test
    fun `When firstActivityCreated is true, start transaction with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = Date(0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        // call only once
        verify(fixture.hub).startTransaction(any(), any(), eq(date), any())
    }

    @Test
    fun `When firstActivityCreated is true, start app start warm span with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = Date(0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        val span = fixture.transaction.children.first()
        assertEquals(span.operation, "app.start.warm")
        assertSame(span.startTimestamp, date)
    }

    @Test
    fun `When firstActivityCreated is true, start app start cold span with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = Date(0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, null)

        val span = fixture.transaction.children.first()
        assertEquals(span.operation, "app.start.cold")
        assertSame(span.startTimestamp, date)
    }

    @Test
    fun `When firstActivityCreated is true, start app start span with Warm description`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = Date(0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        val span = fixture.transaction.children.first()
        assertEquals(span.description, "Warm Start")
        assertSame(span.startTimestamp, date)
    }

    @Test
    fun `When firstActivityCreated is true, start app start span with Cold description`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val date = Date(0)
        setAppStartTime(date)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, null)

        val span = fixture.transaction.children.first()
        assertEquals(span.description, "Cold Start")
        assertSame(span.startTimestamp, date)
    }

    @Test
    fun `When firstActivityCreated is false, start transaction but not with given appStartTime`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        setAppStartTime()

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, fixture.bundle)

        verify(fixture.hub).startTransaction(any(), any(), any<Date>(), any())
        sut.onActivityCreated(activity, fixture.bundle)
        sut.onActivityPostResumed(activity)

        val newActivity = mock<Activity>()
        sut.onActivityPreCreated(newActivity, fixture.bundle)

//        verify(fixture.hub).startTransaction(any<String>(), any())
    }

    private fun setAppStartTime(date: Date = Date(0)) {
        // set by SentryPerformanceProvider so forcing it here
        AppStartState.getInstance().setAppStartTime(0, date)
    }
}
