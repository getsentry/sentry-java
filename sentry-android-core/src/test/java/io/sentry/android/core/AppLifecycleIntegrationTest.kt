package io.sentry.android.core

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IScopes
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AppLifecycleIntegrationTest {

    private class Fixture {
        val scopes = mock<IScopes>()
        lateinit var handler: MainLooperHandler
        val options = SentryAndroidOptions()

        fun getSut(mockHandler: Boolean = true): AppLifecycleIntegration {
            handler = if (mockHandler) mock() else MainLooperHandler()
            return AppLifecycleIntegration(handler)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When AppLifecycleIntegration is added, lifecycle watcher should be started`() {
        val sut = fixture.getSut()

        sut.register(fixture.scopes, fixture.options)

        assertNotNull(sut.watcher)
    }

    @Test
    fun `When SessionTracking and AppLifecycle breadcrumbs are disabled, lifecycle watcher should not be started`() {
        val sut = fixture.getSut()
        fixture.options.apply {
            isEnableAppLifecycleBreadcrumbs = false
            isEnableAutoSessionTracking = false
        }

        sut.register(fixture.scopes, fixture.options)

        assertNull(sut.watcher)
    }

    @Test
    fun `When AppLifecycleIntegration is closed, lifecycle watcher should be closed`() {
        val sut = fixture.getSut()

        sut.register(fixture.scopes, fixture.options)

        assertNotNull(sut.watcher)

        sut.close()

        assertNull(sut.watcher)
    }

    @Test
    fun `When AppLifecycleIntegration is registered from a background thread, post on the main thread`() {
        val sut = fixture.getSut()
        val latch = CountDownLatch(1)

        Thread {
            sut.register(fixture.scopes, fixture.options)
            latch.countDown()
        }.start()

        latch.await()

        verify(fixture.handler).post(any())
    }

    @Test
    fun `When AppLifecycleIntegration is closed from a background thread, post on the main thread`() {
        val sut = fixture.getSut()
        val latch = CountDownLatch(1)

        sut.register(fixture.scopes, fixture.options)

        assertNotNull(sut.watcher)

        Thread {
            sut.close()
            latch.countDown()
        }.start()

        latch.await()

        verify(fixture.handler).post(any())
    }

    @Test
    fun `When AppLifecycleIntegration is closed from a background thread, watcher is set to null`() {
        val sut = fixture.getSut(mockHandler = false)
        val latch = CountDownLatch(1)

        sut.register(fixture.scopes, fixture.options)

        assertNotNull(sut.watcher)

        Thread {
            sut.close()
            latch.countDown()
        }.start()

        latch.await()

        // ensure all messages on main looper got processed
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(sut.watcher)
    }
}
