package io.sentry.android.core

import android.content.Context
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ISentryClient
import io.sentry.Sentry
import io.sentry.Session.State.Exited
import io.sentry.Session.State.Ok
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class SessionTrackingIntegrationTest {

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `test`() {
        lateinit var options: SentryAndroidOptions
        SentryAndroid.init(context) {
            it.dsn = "https://key@sentry.io/proj"
            it.release = "io.sentry.samples@2.3.0"
            it.environment = "production"
            it.sessionTrackingIntervalMillis = 10L
            options = it
        }
        val client = mock<ISentryClient>() // TODO: custom impl
        Sentry.bindClient(client)
// TOOD: helper method to retrieve sessionid for each start/end
        val lifecycle = LifecycleRegistry(mock())
        val lifecycleWatcher = (options.integrations.find {
            it is AppLifecycleIntegration
        } as AppLifecycleIntegration).watcher
        lifecycle.addObserver(lifecycleWatcher!!)

        lifecycle.handleLifecycleEvent(ON_START)
        Thread.sleep(100L)
        lifecycle.handleLifecycleEvent(ON_STOP)

        Thread.sleep(100L)

        lifecycle.handleLifecycleEvent(ON_START)
        Thread.sleep(100L)
        lifecycle.handleLifecycleEvent(ON_STOP)

        inOrder(client) {
            verify(client).captureSession(argThat { status == Ok }, anyOrNull())
            verify(client).captureSession(argThat { status == Exited }, anyOrNull())
            verify(client).captureSession(argThat { status == Ok }, anyOrNull())
            verify(client).captureSession(argThat { status == Exited }, anyOrNull())
        }
    }
}
