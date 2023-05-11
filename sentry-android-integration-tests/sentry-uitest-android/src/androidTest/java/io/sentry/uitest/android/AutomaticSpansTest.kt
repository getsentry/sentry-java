package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AutomaticSpansTest : BaseUiTest() {

    @Test
    fun ttidTtfdSpans() {
        val transactions = mutableListOf<SentryTransaction>()

        initSentry(false) { options: SentryAndroidOptions ->
            options.isDebug = true
            options.setDiagnosticLevel(SentryLevel.DEBUG)
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
            options.isEnableAutoActivityLifecycleTracing = true
            options.isEnableTimeToFullDisplayTracing = true
            options.beforeSendTransaction =
                SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
                    transactions.add(transaction)
                    transaction
                }
        }

        val activity = launchActivity<ComposeActivity>()
        activity.moveToState(Lifecycle.State.RESUMED)
        activity.onActivity {
            Sentry.reportFullyDisplayed()
        }
        activity.moveToState(Lifecycle.State.DESTROYED)

        assertEquals(1, transactions.size)
        assertTrue("TTID span missing") {
            transactions.first().spans.any {
                it.op == "ui.load.initial_display"
            }
        }
        assertTrue("TTFD span missing") {
            transactions.first().spans.any {
                it.op == "ui.load.full_display"
            }
        }
    }
}
