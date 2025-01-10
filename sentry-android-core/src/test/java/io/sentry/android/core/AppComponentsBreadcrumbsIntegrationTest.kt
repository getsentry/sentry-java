package io.sentry.android.core

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.SentryLevel
import io.sentry.test.ImmediateExecutorService
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.NullPointerException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        verify(fixture.context).registerComponentCallbacks(any())
    }

    @Test
    fun `When app components breadcrumb is enabled, but ComponentCallbacks is not ready, do not throw`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        whenever(fixture.context.registerComponentCallbacks(any())).thenThrow(NullPointerException())
        sut.register(scopes, options)
        assertFalse(options.isEnableAppComponentBreadcrumbs)
    }

    @Test
    fun `When app components breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            isEnableAppComponentBreadcrumbs = false
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        verify(fixture.context, never()).registerComponentCallbacks(any())
    }

    @Test
    fun `When AppComponentsBreadcrumbsIntegrationTest is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        sut.close()
        verify(fixture.context).unregisterComponentCallbacks(any())
    }

    @Test
    fun `When app components breadcrumb is closed, but ComponentCallbacks is not ready, do not throw`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        whenever(fixture.context.registerComponentCallbacks(any())).thenThrow(NullPointerException())
        whenever(fixture.context.unregisterComponentCallbacks(any())).thenThrow(NullPointerException())
        sut.register(scopes, options)
        sut.close()
    }

    @Test
    fun `When low memory event, a breadcrumb with type, category and level should be set`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        sut.onLowMemory()
        verify(scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.event", it.category)
                assertEquals("system", it.type)
                assertEquals(SentryLevel.WARNING, it.level)
            }
        )
    }

    @Test
    fun `When trim memory event with level, a breadcrumb with type, category and level should be set`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        sut.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        verify(scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.event", it.category)
                assertEquals("system", it.type)
                assertEquals(SentryLevel.WARNING, it.level)
            }
        )
    }

    @Test
    fun `When trim memory event with level not so high, do not add a breadcrumb`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        sut.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        verify(scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When device orientation event, a breadcrumb with type, category and level should be set`() {
        val sut = AppComponentsBreadcrumbsIntegration(ApplicationProvider.getApplicationContext())
        val options = SentryAndroidOptions().apply {
            executorService = ImmediateExecutorService()
        }
        val scopes = mock<IScopes>()
        sut.register(scopes, options)
        sut.onConfigurationChanged(mock())
        verify(scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.orientation", it.category)
                assertEquals("navigation", it.type)
                assertEquals(SentryLevel.INFO, it.level)
                // cant assert data, its not a public API
            },
            anyOrNull()
        )
    }
}
