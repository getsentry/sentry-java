package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLifecycleIntegrationTest {

    private class Fixture {
        val hub = mock<IHub>()
        val handler = mock<IHandler>()
        val options = SentryAndroidOptions()

        fun getSut(): AppLifecycleIntegration {
            return AppLifecycleIntegration(handler)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When AppLifecycleIntegration is added, lifecycle watcher should be started`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        assertNotNull(sut.watcher)
    }

    @Test
    fun `When SessionTracking and AppLifecycle breadcrumbs are disabled, lifecycle watcher should not be started`() {
        val sut = fixture.getSut()
        fixture.options.apply {
            isEnableAppLifecycleBreadcrumbs = false
            isEnableAutoSessionTracking = false
        }

        sut.register(fixture.hub, fixture.options)

        assertNull(sut.watcher)
    }

    @Test
    fun `When AppLifecycleIntegration is closed, lifecycle watcher should be closed`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        assertNotNull(sut.watcher)

        sut.close()

        assertNull(sut.watcher)
    }

    @Test
    fun `When AppLifecycleIntegration is registered from a background thread, post on the main thread`() {
        val sut = fixture.getSut()
        val latch = CountDownLatch(1)

        Thread {
            sut.register(fixture.hub, fixture.options)
            latch.countDown()
        }.start()

        latch.await()

        verify(fixture.handler).post(any())
    }

    @Test
    fun `When AppLifecycleIntegration is closed from a background thread, post on the main thread`() {
        val sut = fixture.getSut()
        val latch = CountDownLatch(1)

        sut.register(fixture.hub, fixture.options)

        assertNotNull(sut.watcher)

        Thread {
            sut.close()
            latch.countDown()
        }.start()

        latch.await()

        verify(fixture.handler).post(any())
    }
}
