package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import io.sentry.SentryOptions
import leakcanary.LeakAssertions
import leakcanary.LeakCanary
import org.awaitility.kotlin.await
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assume.assumeThat
import org.junit.Before
import shark.AndroidReferenceMatchers
import shark.IgnoredReferenceMatcher
import shark.ReferencePattern
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test

class ReplayTest : BaseUiTest() {

    @Before
    fun setup() {
        // we can't run on GH actions emulator, because they don't allow capturing screenshots properly
        assumeThat(
            System.getProperty("environment") != "github",
            `is`(true)
        )
    }

    @Test
    fun composeReplayDoesNotLeak() {
        val sent = AtomicBoolean(false)

        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults +
                listOf(
                    IgnoredReferenceMatcher(
                        ReferencePattern.InstanceFieldPattern(
                            "com.saucelabs.rdcinjector.testfairy.TestFairyEventQueue",
                            "context"
                        )
                    ),
                    // Seems like a false-positive returned by LeakCanary when curtains is used in
                    // the host application (LeakCanary uses it itself internally). We use kind of
                    // the same approach which possibly clashes with LeakCanary's internal state.
                    // Only the case when replay is enabled.
                    // TODO: check if it's actually a leak on our side, or a false-positive and report to LeakCanary's github issue tracker
                    IgnoredReferenceMatcher(
                        ReferencePattern.InstanceFieldPattern(
                            "curtains.internal.RootViewsSpy",
                            "delegatingViewList"
                        )
                    )
                ) + ('a'..'z').map { char ->
                IgnoredReferenceMatcher(
                    ReferencePattern.StaticFieldPattern(
                        "com.testfairy.modules.capture.TouchListener",
                        "$char"
                    )
                )
            }
        )

        val activityScenario = launchActivity<ComposeActivity>()
        activityScenario.moveToState(Lifecycle.State.RESUMED)

        initSentry {
            it.experimental.sessionReplay.sessionSampleRate = 1.0

            it.beforeSendReplay =
                SentryOptions.BeforeSendReplayCallback { event, _ ->
                    sent.set(true)
                    event
                }
        }

        // wait until first segment is being sent
        await.untilTrue(sent)

        activityScenario.moveToState(Lifecycle.State.DESTROYED)

        LeakAssertions.assertNoLeaks()
    }
}
