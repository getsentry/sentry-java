package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AutomaticSpansTest : BaseUiTest() {

    @Test
    fun ttidTtfdSpans() {
        initSentry(true) { options: SentryAndroidOptions ->
            options.isDebug = true
            options.setDiagnosticLevel(SentryLevel.DEBUG)
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
            options.isEnableAutoActivityLifecycleTracing = true
            options.isEnableTimeToFullDisplayTracing = true
        }

        relayIdlingResource.increment()
        val activity = launchActivity<ComposeActivity>()
        activity.moveToState(Lifecycle.State.RESUMED)
        activity.onActivity {
            Sentry.reportFullyDisplayed()
        }
        activity.moveToState(Lifecycle.State.DESTROYED)

        relay.assert {
            assertFirstEnvelope {
                val transactionItem: SentryTransaction = it.assertTransaction()
                assertTrue("TTID span missing") {
                    transactionItem.spans.any { span ->
                        span.op == "ui.load.initial_display"
                    }
                }
                assertTrue("TTFD span missing") {
                    transactionItem.spans.any { span ->
                        span.op == "ui.load.full_display"
                    }
                }
            }
        }
    }
}
