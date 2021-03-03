package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityLifecycleIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val hub = mock<IHub>()
        val options = SentryAndroidOptions()
        val activity = mock<Activity>()
        val bundle = mock<Bundle>()

        fun getSut(): ActivityLifecycleIntegration {
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
}
