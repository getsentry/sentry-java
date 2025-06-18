package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.sentry.Breadcrumb
import io.sentry.Scopes
import io.sentry.SentryLevel
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityBreadcrumbsIntegrationTest {
    private class Fixture {
        val application = mock<Application>()
        val scopes = mock<Scopes>()
        val options =
            SentryAndroidOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
        val bundle = mock<Bundle>()

        fun getSut(enabled: Boolean = true): ActivityBreadcrumbsIntegration {
            options.isEnableActivityLifecycleBreadcrumbs = enabled
            whenever(scopes.options).thenReturn(options)
            return ActivityBreadcrumbsIntegration(
                application,
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When ActivityBreadcrumbsIntegration is disabled, it should not register the activity callback`() {
        val sut = fixture.getSut(false)
        sut.register(fixture.scopes, fixture.options)

        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is enabled, it should register the activity callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        verify(fixture.application).registerActivityLifecycleCallbacks(any())

        sut.close()
        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `When breadcrumb is added, type and category should be set`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("ui.lifecycle", it.category)
                assertEquals("navigation", it.type)
                assertEquals(SentryLevel.INFO, it.level)
                // cant assert data, its not a public API
            },
            anyOrNull(),
        )
    }

    @Test
    fun `When activity is created, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityCreated(activity, fixture.bundle)

        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is started, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityStarted(activity)

        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is resumed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityResumed(activity)

        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is paused, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityPaused(activity)

        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is stopped, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityStopped(activity)

        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is save instance, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivitySaveInstanceState(activity, fixture.bundle)

        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When activity is destroyed, it should add a breadcrumb`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        val activity = mock<Activity>()
        sut.onActivityDestroyed(activity)

        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }
}
