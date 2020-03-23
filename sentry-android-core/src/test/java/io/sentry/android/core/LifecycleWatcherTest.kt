package io.sentry.android.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.IHub
import kotlin.test.Test

class LifecycleWatcherTest {

    @Test
    fun `if last started session is 0, start new session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 100L)
        watcher.onStart(Owner())
        verify(hub).startSession()
    }

    @Test
    fun `if last started session is after interval, start new session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 100L)
        watcher.onStart(Owner())
        Thread.sleep(150L)
        watcher.onStart(Owner())
        verify(hub, times(2)).startSession()
    }

    @Test
    fun `if last started session is before interval, it should not start a new session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 1000L)
        watcher.onStart(Owner())
        Thread.sleep(100)
        watcher.onStart(Owner())
        verify(hub).startSession()
    }

    @Test
    fun `if app goes to background, end session after interval`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 100L)
        watcher.onStart(Owner())
        watcher.onStop(Owner())
        Thread.sleep(500L)
        verify(hub).endSession()
    }

    @Test
    fun `if app goes to background and foreground again, dont end the session`() {
        val hub = mock<IHub>()
        val watcher = LifecycleWatcher(hub, 1000L)
        watcher.onStart(Owner())
        watcher.onStop(Owner())
        Thread.sleep(150)
        watcher.onStart(Owner())
        verify(hub, never()).endSession()
    }

    internal class Owner : LifecycleOwner {
        override fun getLifecycle(): Lifecycle {
            return LifecycleRegistry(this)
        }
    }
}
