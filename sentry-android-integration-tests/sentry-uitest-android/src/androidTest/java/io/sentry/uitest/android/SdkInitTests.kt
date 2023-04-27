package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroidOptions
import org.junit.runner.RunWith
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class SdkInitTests : BaseUiTest() {

    @Test
    fun doubleInitDoesNotThrow() {
        initSentry(false) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        val transaction = Sentry.startTransaction("e2etests", "testInit")
        val sampleScenario = launchActivity<EmptyActivity>()
        initSentry(false) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        transaction.finish()
        sampleScenario.moveToState(Lifecycle.State.DESTROYED)
        val transaction2 = Sentry.startTransaction("e2etests", "testInit")
        transaction2.finish()
    }
}
