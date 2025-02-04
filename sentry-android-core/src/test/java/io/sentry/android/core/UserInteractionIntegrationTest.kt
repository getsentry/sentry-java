package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Scopes
import io.sentry.android.core.internal.gestures.NoOpWindowCallback
import io.sentry.android.core.internal.gestures.SentryWindowCallback
import junit.framework.TestCase.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class UserInteractionIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val scopes = mock<Scopes>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val activity: Activity = buildActivity(EmptyActivity::class.java).setup().get()
        val window: Window = activity.window
        val loadClass = mock<LoadClass>()

        fun getSut(
            callback: Window.Callback? = null,
            isAndroidXAvailable: Boolean = true
        ): UserInteractionIntegration {
            whenever(loadClass.isClassAvailable(any(), anyOrNull<SentryAndroidOptions>())).thenReturn(isAndroidXAvailable)
            whenever(scopes.options).thenReturn(options)
            if (callback != null) {
                window.callback = callback
            }
            return UserInteractionIntegration(application, loadClass)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when user interaction breadcrumb is enabled registers a callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when user interaction breadcrumb is disabled doesn't register a callback`() {
        val sut = fixture.getSut()
        fixture.options.isEnableUserInteractionBreadcrumbs = false

        sut.register(fixture.scopes, fixture.options)

        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when UserInteractionIntegration is closed unregisters the callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        sut.close()

        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when androidx is unavailable doesn't register a callback`() {
        val sut = fixture.getSut(isAndroidXAvailable = false)

        sut.register(fixture.scopes, fixture.options)

        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `registers window callback on activity resumed`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)

        sut.onActivityResumed(fixture.activity)
        assertIs<SentryWindowCallback>(fixture.activity.window.callback)
    }

    @Test
    fun `when no original callback delegates to NoOpWindowCallback`() {
        val sut = fixture.getSut()
        sut.register(fixture.scopes, fixture.options)
        fixture.window.callback = null

        sut.onActivityResumed(fixture.activity)
        assertIs<SentryWindowCallback>(fixture.activity.window.callback)
        assertIs<NoOpWindowCallback>((fixture.activity.window.callback as SentryWindowCallback).delegate)
    }

    @Test
    fun `unregisters window callback on activity paused`() {
        val sut = fixture.getSut()
        fixture.activity.window.callback = null

        sut.onActivityResumed(fixture.activity)
        sut.onActivityPaused(fixture.activity)

        assertNull(fixture.activity.window.callback)
    }

    @Test
    fun `preserves original callback on activity paused`() {
        val sut = fixture.getSut()
        val mockCallback = mock<Window.Callback>()

        fixture.window.callback = mockCallback

        sut.onActivityResumed(fixture.activity)
        sut.onActivityPaused(fixture.activity)

        assertSame(mockCallback, fixture.activity.window.callback)
    }

    @Test
    fun `stops tracing on activity paused`() {
        val callback = mock<SentryWindowCallback>()
        val sut = fixture.getSut()
        fixture.activity.window.callback = callback

        sut.onActivityPaused(fixture.activity)

        verify(callback).stopTracking()
    }

    @Test
    fun `does not instrument if the callback is already ours`() {
        val existingCallback = SentryWindowCallback(
            NoOpWindowCallback(),
            fixture.activity,
            mock(),
            mock()
        )
        val sut = fixture.getSut(existingCallback)

        sut.register(fixture.scopes, fixture.options)
        sut.onActivityResumed(fixture.activity)

        assertNotEquals(existingCallback, (fixture.window.callback as SentryWindowCallback).delegate)
    }
}

private class EmptyActivity : Activity()
