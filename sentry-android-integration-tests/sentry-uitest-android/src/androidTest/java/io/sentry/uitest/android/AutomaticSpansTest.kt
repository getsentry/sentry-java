package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.AndroidLogger
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.assertEnvelopeTransaction
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.SentryTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Assume
import org.junit.runner.RunWith

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
    activity.onActivity { Sentry.reportFullyDisplayed() }
    activity.moveToState(Lifecycle.State.DESTROYED)

    relay.assert {
      assertFirstEnvelope {
        val transactionItem: SentryTransaction = it.assertTransaction()
        assertTrue("TTID span missing") {
          transactionItem.spans.any { span -> span.op == "ui.load.initial_display" }
        }
        assertTrue("TTFD span missing") {
          transactionItem.spans.any { span -> span.op == "ui.load.full_display" }
        }
      }
    }
  }

  @Test
  fun checkAppStartFramesMeasurements() {
    initSentry(true) { options: SentryAndroidOptions ->
      options.tracesSampleRate = 1.0
      options.isEnableTimeToFullDisplayTracing = true
      options.isEnablePerformanceV2 = false
    }

    IdlingRegistry.getInstance().register(ProfilingSampleActivity.scrollingIdlingResource)
    val sampleScenario = launchActivity<ProfilingSampleActivity>()
    swipeList(3)
    Sentry.reportFullyDisplayed()
    sampleScenario.moveToState(Lifecycle.State.DESTROYED)
    IdlingRegistry.getInstance().unregister(ProfilingSampleActivity.scrollingIdlingResource)
    relayIdlingResource.increment()

    relay.assert {
      findEnvelope {
          assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction ==
            "ProfilingSampleActivity"
        }
        .assert {
          val transactionItem: SentryTransaction = it.assertTransaction()
          it.assertNoOtherItems()
          val measurements = transactionItem.measurements
          val frozenFrames = measurements[MeasurementValue.KEY_FRAMES_FROZEN]?.value?.toInt() ?: 0
          val slowFrames = measurements[MeasurementValue.KEY_FRAMES_SLOW]?.value?.toInt() ?: 0
          val totalFrames = measurements[MeasurementValue.KEY_FRAMES_TOTAL]?.value?.toInt() ?: 0
          assertEquals("ProfilingSampleActivity", transactionItem.transaction)
          // AGP matrix tests have no frames
          Assume.assumeTrue(totalFrames > 0)
          assertNotEquals(totalFrames, 0)
          assertTrue(
            totalFrames > slowFrames + frozenFrames,
            "Expected total frames ($totalFrames) to be higher than the sum of slow ($slowFrames) and frozen ($frozenFrames) frames.",
          )
        }
      assertNoOtherEnvelopes()
    }
  }

  @Test
  fun checkAppStartFramesMeasurementsPerfV2() {
    initSentry(true) { options: SentryAndroidOptions ->
      options.tracesSampleRate = 1.0
      options.isEnableTimeToFullDisplayTracing = true
      options.isEnablePerformanceV2 = true
    }

    IdlingRegistry.getInstance().register(ProfilingSampleActivity.scrollingIdlingResource)
    val sampleScenario = launchActivity<ProfilingSampleActivity>()
    swipeList(3)
    Sentry.reportFullyDisplayed()
    sampleScenario.moveToState(Lifecycle.State.DESTROYED)
    IdlingRegistry.getInstance().unregister(ProfilingSampleActivity.scrollingIdlingResource)
    relayIdlingResource.increment()

    relay.assert {
      findEnvelope {
          assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction ==
            "ProfilingSampleActivity"
        }
        .assert {
          val transactionItem: SentryTransaction = it.assertTransaction()
          it.assertNoOtherItems()
          val measurements = transactionItem.measurements
          val frozenFrames = measurements[MeasurementValue.KEY_FRAMES_FROZEN]?.value?.toInt() ?: 0
          val slowFrames = measurements[MeasurementValue.KEY_FRAMES_SLOW]?.value?.toInt() ?: 0
          val totalFrames = measurements[MeasurementValue.KEY_FRAMES_TOTAL]?.value?.toInt() ?: 0
          assertEquals("ProfilingSampleActivity", transactionItem.transaction)
          // AGP matrix tests have no frames
          Assume.assumeTrue(totalFrames > 0)
          assertNotEquals(totalFrames, 0)
          assertTrue(
            totalFrames > slowFrames + frozenFrames,
            "Expected total frames ($totalFrames) to be higher than the sum of slow ($slowFrames) and frozen ($frozenFrames) frames.",
          )
        }
      assertNoOtherEnvelopes()
    }
  }

  private fun swipeList(times: Int) {
    repeat(times) {
      Thread.sleep(100)
      Espresso.onView(ViewMatchers.withId(R.id.profiling_sample_list))
        .perform(ViewActions.swipeUp())
      Espresso.onIdle()
    }
  }
}
