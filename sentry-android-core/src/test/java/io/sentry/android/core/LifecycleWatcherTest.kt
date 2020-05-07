package io.sentry.android.core

import androidx.lifecycle.LifecycleOwner
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.Breadcrumb
import io.sentry.core.IHub
import io.sentry.core.SentryLevel
import io.sentry.core.transport.ICurrentDateProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.awaitility.kotlin.await

class LifecycleWatcherTest {

    private class Fixture {
        val ownerMock = mock<LifecycleOwner>()
        val hub = mock<IHub>()
        val dateProvider = mock<ICurrentDateProvider>()

        fun getSUT(sessionIntervalMillis: Long = 0L, enableSessionTracking: Boolean = true, enableAppLifecycleBreadcrumbs: Boolean = true): LifecycleWatcher {
            return LifecycleWatcher(hub, sessionIntervalMillis, enableSessionTracking, enableAppLifecycleBreadcrumbs, dateProvider)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `if last started session is 0, start new session`() {
        val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub).startSession()
    }

    @Test
    fun `if last started session is after interval, start new session`() {
        val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
        whenever(fixture.dateProvider.currentTimeMillis).thenReturn(1L, 2L)
        watcher.onStart(fixture.ownerMock)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub, times(2)).startSession()
    }

    @Test
    fun `if last started session is before interval, it should not start a new session`() {
        val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
        whenever(fixture.dateProvider.currentTimeMillis).thenReturn(2L, 1L)
        watcher.onStart(fixture.ownerMock)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub).startSession()
    }

    @Test
    fun `if app goes to background, end session after interval`() {
        val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
        watcher.onStart(fixture.ownerMock)
        watcher.onStop(fixture.ownerMock)
        await.untilFalse(watcher.isRunningSession)
        verify(fixture.hub).endSession()
    }

    @Test
    fun `if app goes to background and foreground again, dont end the session`() {
        val watcher = fixture.getSUT(sessionIntervalMillis = 30000L, enableAppLifecycleBreadcrumbs = false)
        watcher.onStart(fixture.ownerMock)

        watcher.onStop(fixture.ownerMock)
        assertNotNull(watcher.timerTask)

        watcher.onStart(fixture.ownerMock)
        assertNull(watcher.timerTask)

        verify(fixture.hub, never()).endSession()
    }

    @Test
    fun `When session tracking is disabled, do not start session`() {
        val watcher = fixture.getSUT(enableSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub, never()).startSession()
    }

    @Test
    fun `When session tracking is disabled, do not end session`() {
        val watcher = fixture.getSUT(enableSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
        watcher.onStop(fixture.ownerMock)
        assertNull(watcher.timerTask)
        verify(fixture.hub, never()).endSession()
    }

    @Test
    fun `When session tracking is enabled, add breadcrumb on start`() {
        val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("session", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When session tracking is enabled, add breadcrumb on stop`() {
        val watcher = fixture.getSUT(enableAppLifecycleBreadcrumbs = false)
        watcher.isRunningSession.set(true)
        watcher.onStop(fixture.ownerMock)
        await.untilFalse(watcher.isRunningSession)
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("session", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When session tracking is disabled, do not add breadcrumb on start`() {
        val watcher = fixture.getSUT(enableSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When session tracking is disabled, do not add breadcrumb on stop`() {
        val watcher = fixture.getSUT(enableSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
        watcher.onStop(fixture.ownerMock)
        assertNull(watcher.timerTask)
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When app lifecycle breadcrumbs is enabled, add breadcrumb on start`() {
        val watcher = fixture.getSUT(enableSessionTracking = false)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("navigation", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When app lifecycle breadcrumbs is disabled, do not add breadcrumb on start`() {
        val watcher = fixture.getSUT(enableSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
        watcher.onStart(fixture.ownerMock)
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When app lifecycle breadcrumbs is enabled, add breadcrumb on stop`() {
        val watcher = fixture.getSUT(enableSessionTracking = false)
        watcher.onStop(fixture.ownerMock)
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("app.lifecycle", it.category)
            assertEquals("navigation", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }

    @Test
    fun `When app lifecycle breadcrumbs is disabled, do not add breadcrumb on stop`() {
        val watcher = fixture.getSUT(enableSessionTracking = false, enableAppLifecycleBreadcrumbs = false)
        watcher.onStop(fixture.ownerMock)
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }
}
