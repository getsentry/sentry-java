package io.sentry.android.replay

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ScreenshotStrategyType
import io.sentry.SentryOptions
import io.sentry.android.replay.ReplaySmokeTest.Fixture
import io.sentry.android.replay.screenshot.CanvasStrategy
import io.sentry.android.replay.screenshot.PixelCopyStrategy
import io.sentry.android.replay.screenshot.ScreenshotStrategy
import io.sentry.android.replay.util.MainLooperHandler
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class ScreenshotRecorderTest {

  internal class Fixture() {

    fun getSut(config: (options: SentryOptions) -> Unit = {}): ScreenshotRecorder {
      val options = SentryOptions()
      config(options)
      return ScreenshotRecorder(
        ScreenshotRecorderConfig(100, 100, 1f, 1f, 1, 1000),
        options,
        object : ExecutorProvider {
          override fun getExecutor(): ScheduledExecutorService = mock<ScheduledExecutorService>()

          override fun getMainLooperHandler(): MainLooperHandler = mock<MainLooperHandler>()
        },
        null,
      )
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when config uses PIXEL_COPY strategy, ScreenshotRecorder creates PixelCopyStrategy`() {
    val recorder =
      fixture.getSut { options ->
        options.sessionReplay.screenshotStrategy = ScreenshotStrategyType.PIXEL_COPY
      }

    val strategy = getStrategy(recorder)

    assertTrue(
      strategy is PixelCopyStrategy,
      "Expected PixelCopyStrategy but got ${strategy::class.simpleName}",
    )
  }

  @Test
  fun `when config uses CANVAS strategy, ScreenshotRecorder creates CanvasStrategy`() {
    val recorder =
      fixture.getSut { options ->
        options.sessionReplay.screenshotStrategy = ScreenshotStrategyType.CANVAS
      }
    val strategy = getStrategy(recorder)

    assertTrue(
      strategy is CanvasStrategy,
      "Expected CanvasStrategy but got ${strategy::class.simpleName}",
    )
  }

  @Test
  fun `when config uses default strategy, ScreenshotRecorder creates PixelCopyStrategy`() {
    val recorder = fixture.getSut()
    val strategy = getStrategy(recorder)

    assertTrue(
      strategy is PixelCopyStrategy,
      "Expected PixelCopyStrategy as default but got ${strategy::class.simpleName}",
    )
  }

  private fun getStrategy(recorder: ScreenshotRecorder): ScreenshotStrategy {
    val strategyField = ScreenshotRecorder::class.java.getDeclaredField("screenshotStrategy")
    strategyField.isAccessible = true
    return strategyField.get(recorder) as ScreenshotStrategy
  }
}
