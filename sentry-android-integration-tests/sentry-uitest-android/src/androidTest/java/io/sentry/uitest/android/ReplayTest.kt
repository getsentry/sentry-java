package io.sentry.uitest.android

import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions
import io.sentry.TypeCheckHint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import leakcanary.LeakAssertions
import leakcanary.LeakCanary
import org.awaitility.kotlin.await
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assume.assumeThat
import org.junit.Before
import shark.AndroidReferenceMatchers
import shark.IgnoredReferenceMatcher
import shark.ReferencePattern

class ReplayTest : BaseUiTest() {
  @Before
  fun setup() {
    assumeThat(isReplayAvailable(), `is`(true))
    // crash on swallowed Compose masking errors (e.g. broken obfuscated internals) so regressions
    // fail this on-device test instead of silently under-masking (see SentryReplayDebug)
    System.setProperty("io.sentry.replay.compose.fail-fast", "true")
  }

  @Test
  fun composeReplayDoesNotLeak() {
    val sent = AtomicBoolean(false)

    LeakCanary.config =
      LeakCanary.config.copy(
        referenceMatchers =
          AndroidReferenceMatchers.appDefaults +
            listOf(
              IgnoredReferenceMatcher(
                ReferencePattern.InstanceFieldPattern(
                  "com.saucelabs.rdcinjector.testfairy.TestFairyEventQueue",
                  "context",
                ),
              ),
              // Seems like a false-positive returned by LeakCanary when curtains is used in
              // the host application (LeakCanary uses it itself internally). We use kind of
              // the same approach which possibly clashes with LeakCanary's internal state.
              // Only the case when replay is enabled.
              // TODO: check if it's actually a leak on our side, or a false-positive and report to
              // LeakCanary's github issue tracker
              IgnoredReferenceMatcher(
                ReferencePattern.InstanceFieldPattern(
                  "curtains.internal.RootViewsSpy",
                  "delegatingViewList",
                ),
              ),
            ) +
            ('a'..'z').map { char ->
              IgnoredReferenceMatcher(
                ReferencePattern.StaticFieldPattern(
                  "com.testfairy.modules.capture.TouchListener",
                  "$char",
                ),
              )
            },
      )

    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)

    initSentry {
      it.sessionReplay.sessionSampleRate = 1.0

      it.beforeSendReplay = SentryOptions.BeforeSendReplayCallback { event, _ ->
        sent.set(true)
        event
      }
    }

    // wait until first segment is being sent
    await.untilTrue(sent)

    activityScenario.moveToState(Lifecycle.State.DESTROYED)

    LeakAssertions.assertNoLeaks()
  }

  @Test
  fun composeCoilImageIsMasked() {
    val frame = AtomicReference<Bitmap>()
    val frameReceived = CountDownLatch(1)

    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)
    // an image that hasn't loaded yet is empty, and would look masked
    assertTrue(ComposeActivity.imageLoaded.await(10, TimeUnit.SECONDS), "Coil image didn't load")

    initSentry {
      it.sessionReplay.sessionSampleRate = 1.0
      it.sessionReplay.frameObserver = SentryReplayOptions.ReplayFrameObserver { hint, _, _ ->
        val bitmap = hint.getAs(TypeCheckHint.REPLAY_FRAME_BITMAP, Bitmap::class.java)
        if (bitmap != null && frame.compareAndSet(null, bitmap)) {
          frameReceived.countDown()
        } else {
          bitmap?.recycle()
        }
      }
    }
    assertTrue(frameReceived.await(10, TimeUnit.SECONDS), "Expected a replay frame")

    var rootWidth = 0
    var rootHeight = 0
    activityScenario.onActivity {
      rootWidth = it.window.decorView.width
      rootHeight = it.window.decorView.height
    }
    val bitmap = frame.get()
    val scaleX = bitmap.width / rootWidth.toFloat()
    val scaleY = bitmap.height / rootHeight.toFloat()
    val bounds = ComposeActivity.imageBounds
    // the inner half of the image, away from the edges of the scaled mask
    val colors = mutableSetOf<Int>()
    for (i in 1..9) {
      for (j in 1..9) {
        val x = (bounds.left + bounds.width * (0.25f + i * 0.05f)) * scaleX
        val y = (bounds.top + bounds.height * (0.25f + j * 0.05f)) * scaleY
        colors += bitmap.getPixel(x.toInt(), y.toInt())
      }
    }
    bitmap.recycle()
    activityScenario.moveToState(Lifecycle.State.DESTROYED)

    assertEquals(1, colors.size, "Coil image isn't masked in the replay frame")
  }
}
