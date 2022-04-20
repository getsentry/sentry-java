package io.sentry.android.uitests.end2end

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest : BaseUiTest() {

    @Test
    fun useAppContext() {

        Sentry.init {
            it.dsn = "http://12345678901234567890123456789012@${servers.relay.hostName}:${servers.relay.port}/1234567"
        }

        val emptyActivity = launchActivity<EmptyActivity>()
        Sentry.captureMessage("aaaaa")
        Thread.sleep(10000)
        emptyActivity.moveToState(Lifecycle.State.DESTROYED)
        // Context of the app under test.
        assertEquals("io.sentry.android.uitests.end2end.test", context.packageName)
    }
}
