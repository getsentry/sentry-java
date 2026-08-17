package io.sentry.android.buddy

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class SentryBuddyTest {
  @AfterTest
  fun tearDown() {
    SentryBuddy.resetForTest()
  }

  @Test
  fun `start requires install`() {
    assertFailsWith<IllegalStateException> {
      SentryBuddy.startRecording(BuddyFlowIntent("Checkout"))
    }
  }

  @Test
  fun `activity resume records screen through installed facade`() {
    val application = RuntimeEnvironment.getApplication()
    SentryBuddy.install(application)

    SentryBuddy.startRecording(BuddyFlowIntent("Checkout"))
    Robolectric.buildActivity(Activity::class.java).setup()
    val recording = SentryBuddy.stopRecording()

    assertThat(recording.timeline.map { it.type }).contains(BuddyTimelineItem.Type.SCREEN)
    assertThat(recording.summary.screenCount).isEqualTo(1)
  }
}
