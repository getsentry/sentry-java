package io.sentry.android.replay.capture

import kotlin.test.Test
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ReplaySegmentHintTest {
  @get:Rule val tmpDir = TemporaryFolder()

  @Test
  fun `markDiscarded deletes the segment video`() {
    val video = tmpDir.newFile("0.mp4")

    ReplaySegmentHint(video).markDiscarded()

    assertFalse(video.exists())
  }
}
