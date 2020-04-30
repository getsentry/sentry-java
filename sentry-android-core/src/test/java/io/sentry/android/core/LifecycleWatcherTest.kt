package io.sentry.android.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.Breadcrumb
import io.sentry.core.IHub
import io.sentry.core.SentryLevel
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class LifecycleWatcherTest {

    @Test
    fun `if last started session is 0, start new session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 100L, true, false)
        watcher.onStart(mock())
        verify(hub).startSession()
    }

    @Test
    fun `if last started session is after interval, start new session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 100L, true, false)
        watcher.onStart(mock())
        Thread.sleep(150L)
        watcher.onStart(mock())
        verify(hub, times(2)).startSession()
    }

    @Test
    fun `if last started session is before interval, it should not start a new session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 1000L, true, false)
        watcher.onStart(mock())
        Thread.sleep(100)
        watcher.onStart(mock())
        verify(hub).startSession()
    }

    @Ignore("for some reason this is flaky only on appveyor")
    @Test
    fun `if app goes to background, end session after interval`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 100L, true, false)
        watcher.onStart(mock())
        watcher.onStop(mock())
        Thread.sleep(500L)
        verify(hub).endSession()
    }

    @Test
    fun `if app goes to background and foreground again, dont end the session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 1000L, true, false)
        watcher.onStart(mock())
        watcher.onStop(mock())
        Thread.sleep(150)
        watcher.onStart(mock())
        verify(hub, never()).endSession()
    }

    @Test
    fun `When session tracking is disabled, do not start session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 1000L, false, false)
        watcher.onStart(mock())
        verify(hub, never()).startSession()
    }

    @Test
    fun `When session tracking is disabled, do not end session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, false, false)
        watcher.onStart(mock())
        verify(hub, never()).endSession()
    }

    @Test
    fun `When session tracking is enabled, add breadcrumb on start`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, true, false)
        watcher.onStart(mock())
        Thread.sleep(150)
        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("session", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When session tracking is enabled, add breadcrumb on stop`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, true, false)
        watcher.onStop(mock())
        Thread.sleep(150)
        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("session", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When session tracking is disabled, do not add breadcrumb on start`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, false, false)
        watcher.onStart(mock())
        verify(hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When session tracking is disabled, do not add breadcrumb on stop`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, false, false)
        watcher.onStop(mock())
        verify(hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When app lifecycle breadcrumbs is enabled, add breadcrumb on start`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, false, true)
        watcher.onStart(mock())
        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("navigation", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When app lifecycle breadcrumbs is disabled, do not add breadcrumb on start`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, false, false)
        watcher.onStart(mock())
        verify(hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When app lifecycle breadcrumbs is enabled, add breadcrumb on stop`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, false, true)
        watcher.onStop(mock())
        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("navigation", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When app lifecycle breadcrumbs is disabled, do not add breadcrumb on stop`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 0L, false, false)
        watcher.onStop(mock())
        verify(hub, never()).addBreadcrumb(any<Breadcrumb>())
    }
}
