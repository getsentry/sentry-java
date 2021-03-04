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
import io.sentry.SentryTransaction
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityLifecycleIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val hub = mock<Hub>()
        val options = SentryAndroidOptions()
        val activity = mock<Activity>()
        val bundle = mock<Bundle>()
        val transaction = SentryTracer(TransactionContext("name", "op"), hub)

        fun getSut(): ActivityLifecycleIntegration {
            whenever(hub.startTransaction(any<String>(), any())).thenReturn(transaction)
            return ActivityLifecycleIntegration(application)
        }
    }

    private val fixture = Fixture()

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

        sut.onActivityCreated(fixture.activity, fixture.bundle)
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
        sut.onActivityCreated(fixture.activity, fixture.bundle)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is started, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        sut.onActivityStarted(fixture.activity)

        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is resumed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityResumed(fixture.activity)
        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is paused, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPaused(fixture.activity)
        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is stopped, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityStopped(fixture.activity)
        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is save instance, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivitySaveInstanceState(fixture.activity, fixture.bundle)
        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is destroyed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityDestroyed(fixture.activity)
        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When tracing is disabled, do not start tracing`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)

        verify(fixture.hub, never()).startTransaction(any<String>(), any())
    }

    @Test
    fun `When tracing is enabled but activity is running, do not start tracing again`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
        sut.onActivityPreCreated(fixture.activity, fixture.bundle)

        // call only once
        verify(fixture.hub).startTransaction(any<String>(), any())
    }

    @Test
    fun `Transaction op is navigation`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)

        verify(fixture.hub).startTransaction(any<String>(), check {
            assertEquals("navigation", it)
        })
    }

    @Test
    fun `Transaction name is the Activity's name`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)

        verify(fixture.hub).startTransaction(check<String> {
            assertEquals("Activity", it)
        }, any())
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

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
    }

    @Test
    fun `When transaction is created, do not overwrite transaction already bound to the Scope`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0

        sut.register(fixture.hub, fixture.options)

        whenever(fixture.hub.configureScope(any())).thenAnswer {
            val scope = Scope(fixture.options)
            val previousTransaction = SentryTracer(TransactionContext("name", "op"), fixture.hub)
            scope.setTransaction(previousTransaction)

            sut.applyScope(scope, fixture.transaction)

            assertEquals(previousTransaction, scope.transaction)
        }

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
    }

    @Test
    fun `When tracing auto finish is enabled, it stops the transaction on onActivityPostResumed`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
        sut.onActivityPostResumed(fixture.activity)

        verify(fixture.hub).captureTransaction(check<SentryTransaction> {
            assertEquals(SpanStatus.OK, it.status)
        })
    }

    @Test
    fun `When tracing has status, do not overwrite it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)

        fixture.transaction.status = SpanStatus.UNKNOWN_ERROR

        sut.onActivityPostResumed(fixture.activity)

        verify(fixture.hub).captureTransaction(check<SentryTransaction> {
            assertEquals(SpanStatus.UNKNOWN_ERROR, it.status)
        })
    }

    @Test
    fun `When tracing auto finish is disabled, do not finish transaction`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        fixture.options.isEnableActivityLifecycleTracingAutoFinish = false
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
        sut.onActivityPostResumed(fixture.activity)

        verify(fixture.hub, never()).captureTransaction(any<SentryTransaction>(), eq(null))
    }

    @Test
    fun `When tracing is disabled, do not finish transaction`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPostResumed(fixture.activity)

        verify(fixture.hub, never()).captureTransaction(any<SentryTransaction>(), eq(null))
    }

    @Test
    fun `When Activity is destroyed but transaction is running, finish it`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
        sut.onActivityDestroyed(fixture.activity)

        verify(fixture.hub).captureTransaction(any<SentryTransaction>())
    }

    @Test
    fun `When transaction is started, adds to WeakWef`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)

        assertFalse(sut.activitiesWithOngoingTransactions.isEmpty())
    }

    @Test
    fun `When Activity is destroyed removes WeakRef`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
        sut.onActivityDestroyed(fixture.activity)

        assertTrue(sut.activitiesWithOngoingTransactions.isEmpty())
    }

    @Test
    fun `When new Activity and transaction is created, finish previous ones`() {
        val sut = fixture.getSut()
        fixture.options.tracesSampleRate = 1.0
        sut.register(fixture.hub, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPreCreated(activity, mock())

        sut.onActivityPreCreated(fixture.activity, fixture.bundle)
        verify(fixture.hub).captureTransaction(any<SentryTransaction>())
    }
}
