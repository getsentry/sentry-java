package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.android.core.R
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.uitest.android.mockservers.REPLAY_VIDEO_PART
import io.sentry.uitest.android.mockservers.replayVideoParts
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import org.awaitility.kotlin.await
import org.junit.runner.RunWith

/**
 * Reproduces a customer flow that combines Session Replay with the user feedback form: opening the
 * form flushes the replay, and discarding the form stops it. Stopping deletes the replay cache
 * directory, while the segments that the flush produced can still be waiting in the transport
 * queue. Their videos are read from disk only when the envelope is written to the socket, so a
 * deleted directory turns a queued segment into a segment without a video, which relay discards as
 * `invalid_replay_video`.
 */
@RunWith(AndroidJUnit4::class)
class ReplayFeedbackDialogTest : BaseUiTest() {

  @Test
  fun discardedFeedbackKeepsVideoOfBufferedSegments() {
    runDiscardedFeedbackFlow { it.sessionReplay.onErrorSampleRate = 1.0 }
  }

  @Test
  fun discardedFeedbackKeepsVideoOfSessionSegments() {
    runDiscardedFeedbackFlow { it.sessionReplay.sessionSampleRate = 1.0 }
  }

  private fun runDiscardedFeedbackFlow(configureReplay: (SentryAndroidOptions) -> Unit) {
    initSentry { options ->
      configureReplay(options)
      // The transport is held open on purpose below; do not let it time out while it waits.
      options.readTimeoutMillis = SECONDS.toMillis(TRANSPORT_HOLD_SECONDS + 10).toInt()
      // What the customer does when the user discards the form.
      options.feedbackOptions.onFormClose = Runnable {
        Sentry.replay().stop()
        Sentry.replay().startBuffering()
      }
    }

    val scenario = launchActivity<EmptyActivity>()
    scenario.moveToState(Lifecycle.State.RESUMED)
    // A segment without frames produces no video at all, so let the recorder fill one first.
    await.atMost(20, SECONDS).until { replayFrameCount() >= 2 }

    // Hold the transport on an unrelated envelope so that the replay segments are still queued when
    // the form is discarded. On a real device the same window is opened by a slow upload.
    relay.addResponse { MockResponse().setHeadersDelay(TRANSPORT_HOLD_SECONDS, SECONDS) }
    Sentry.captureMessage("occupies the transport thread")

    // Opening the form flushes the replay (SentryUserFeedbackForm calls captureReplay).
    scenario.onActivity { Sentry.feedback().show() }
    onView(withId(R.id.sentry_dialog_user_feedback_layout))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))

    // Discarding the form runs onFormClose, which stops the replay.
    onView(withId(R.id.sentry_dialog_user_feedback_btn_cancel)).inRoot(isDialog()).perform(click())

    await.atMost(TRANSPORT_HOLD_SECONDS + 30, SECONDS).untilAsserted {
      relay.assert {
        val segments = peekEnvelopes { it.replayVideoParts() != null }
        assertTrue(segments.isNotEmpty(), "No replay segment reached the server")
        segments.forEach { envelope ->
          val video = envelope.replayVideoParts()!![REPLAY_VIDEO_PART]
          assertNotNull(
            video,
            "Segment ${envelope.header.eventId} was sent without its video, " +
              "so relay discards it as invalid_replay_video",
          )
          assertTrue(video.isNotEmpty(), "Segment ${envelope.header.eventId} has an empty video")
        }
      }
    }
  }

  /** Number of frame screenshots that the recorder has written to the replay cache. */
  private fun replayFrameCount(): Int {
    val cacheDirPath = Sentry.getCurrentScopes().options.cacheDirPath ?: return 0
    return File(cacheDirPath)
      .listFiles { file -> file.isDirectory && file.name.startsWith("replay_") }
      .orEmpty()
      .sumOf { replayDir ->
        replayDir.listFiles { file -> file.name.endsWith(".jpg") }.orEmpty().size
      }
  }

  companion object {
    private const val TRANSPORT_HOLD_SECONDS = 10L
  }
}
