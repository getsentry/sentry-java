package io.sentry.android.replay

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.util.MainLooperHandler
import java.util.concurrent.ScheduledExecutorService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class WindowRecorderTest {
  @Test
  fun `configuration does not start capture while paused`() {
    val mainLooperHandler = mock<MainLooperHandler>()
    val recorder =
      WindowRecorder(
        SentryOptions(),
        windowCallback = mock(),
        mainLooperHandler = mainLooperHandler,
        replayExecutor = mock<ScheduledExecutorService>(),
      )

    recorder.start()
    recorder.pause()
    recorder.onConfigurationChanged(ScreenshotRecorderConfig(100, 200, 1f, 1f, 1, 20_000))

    verify(mainLooperHandler, never()).postDelayed(anyOrNull(), any())

    recorder.resume()

    verify(mainLooperHandler).post(any())
  }
}
