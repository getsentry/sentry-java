package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppComponentsBreadcrumbsIntegrationTest {

    private class Fixture {
        val context = mock<Context>()

        fun getSut(): AppComponentsBreadcrumbsIntegration {
            return AppComponentsBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When app components breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.context).registerComponentCallbacks(any())
    }

    @Test
    fun `When app components breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            isEnableAppComponentBreadcrumbs = false
        }
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.context, never()).registerComponentCallbacks(any())
    }

    @Test
    fun `When AppComponentsBreadcrumbsIntegrationTest is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.close()
        verify(fixture.context).unregisterComponentCallbacks(any())
    }

    @Test
    fun `When low memory event, a breadcrumb with type, category and level should be set`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onLowMemory()
        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("device.event", it.category)
            assertEquals("system", it.type)
            assertEquals(SentryLevel.WARNING, it.level)
        })
    }

    @Test
    fun `When device orientation event, a breadcrumb with type, category and level should be set`() {
        val sut = AppComponentsBreadcrumbsIntegration(ApplicationProvider.getApplicationContext())
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onConfigurationChanged(mock())
        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("device.orientation", it.category)
            assertEquals("navigation", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }
}
