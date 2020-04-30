package io.sentry.android.core

import android.app.Application
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.Breadcrumb
import io.sentry.core.IHub
import io.sentry.core.SentryLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityBreadcrumbsIntegrationTest {

    private class Fixture {
        val application = mock<Application>()

        fun getSut(): ActivityBreadcrumbsIntegration {
            return ActivityBreadcrumbsIntegration(application)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When activity lifecycle breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When activity lifecycle breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            isEnableActivityLifecycleBreadcrumbs = false
        }
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.close()
        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When breadcrumb is added, type and category should be set`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivityCreated(mock(), mock())
        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("ui.lifecycle", it.category)
            assertEquals("navigation", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When activity is created, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivityCreated(mock(), mock())
        verify(hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is started, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivityStarted(mock())
        verify(hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is resumed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivityResumed(mock())
        verify(hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is paused, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivityPaused(mock())
        verify(hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is stopped, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivityStopped(mock())
        verify(hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is save instance, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivitySaveInstanceState(mock(), mock())
        verify(hub).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When activity is destroyed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onActivityDestroyed(mock())
        verify(hub).addBreadcrumb(any<Breadcrumb>())
    }
}
