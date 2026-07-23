package io.sentry.android.replay

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ScreenshotStrategyType
import io.sentry.SentryOptions
import io.sentry.android.replay.ReplaySmokeTest.Fixture
import io.sentry.android.replay.screenshot.CanvasStrategy
import io.sentry.android.replay.screenshot.DeferredWindowPixelCopyShadow
import io.sentry.android.replay.screenshot.PixelCopyStrategy
import io.sentry.android.replay.screenshot.ScreenshotStrategy
import io.sentry.android.replay.util.MainLooperHandler
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
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

          override fun getBackgroundHandler(): Handler = mock<Handler>()
        },
        null,
      )
    }

    fun getCapturingSut(callback: ScreenshotRecorderCallback): ScreenshotRecorder {
      val executor = mock<ScheduledExecutorService>()
      whenever(executor.submit(any<Runnable>())).doAnswer {
        (it.arguments[0] as Runnable).run()
        mock<Future<*>>()
      }
      return ScreenshotRecorder(
        ScreenshotRecorderConfig(100, 100, 1f, 1f, 1, 1000),
        SentryOptions(),
        object : ExecutorProvider {
          override fun getExecutor(): ScheduledExecutorService = executor

          override fun getMainLooperHandler(): MainLooperHandler = MainLooperHandler()

          override fun getBackgroundHandler(): Handler = mock()
        },
        callback,
      )
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setup() {
    System.setProperty("robolectric.areWindowsMarkedVisible", "true")
    System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
    DeferredWindowPixelCopyShadow.reset()
  }

  @Test
  fun `when config uses PIXEL_COPY strategy, ScreenshotRecorder creates PixelCopyStrategy`() {
    val recorder = fixture.getSut { options ->
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
    val recorder = fixture.getSut { options ->
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

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class], sdk = [30])
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `pause invalidates pending capture and resume requests a fresh frame`() {
    val activity = buildActivity(RecorderActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val callback = mock<ScreenshotRecorderCallback>()
    val recorder = fixture.getCapturingSut(callback)
    recorder.bind(activity.get().findViewById(android.R.id.content))

    recorder.capture()
    recorder.pause()
    recorder.resume()
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(callback, never()).onScreenshotRecorded(any<Bitmap>())

    recorder.capture()
    assertEquals(1, DeferredWindowPixelCopyShadow.pendingCount())
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(callback).onScreenshotRecorded(any<Bitmap>())
    recorder.close()
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class], sdk = [30])
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `unbind clears a structurally removed root`() {
    val activity = buildActivity(RecorderActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val recorder = fixture.getCapturingSut(mock())
    val root = activity.get().findViewById<View>(android.R.id.content)
    recorder.bind(root)

    recorder.unbind(root)
    recorder.capture()

    assertEquals(0, DeferredWindowPixelCopyShadow.pendingCount())
    recorder.close()
  }

  @Test
  fun `configuration replacement closes the previous screenshot recorder`() {
    val executor = mock<ScheduledExecutorService>()
    val recorder =
      WindowRecorder(
        SentryOptions(),
        windowCallback = mock(),
        mainLooperHandler = mock(),
        replayExecutor = executor,
      )
    val config = ScreenshotRecorderConfig(100, 100, 1f, 1f, 1, 1000)
    recorder.start()

    recorder.onConfigurationChanged(config)
    recorder.onConfigurationChanged(config.copy(recordingWidth = 112))

    verify(executor).submit(any<Runnable>())
    recorder.close()
    verify(executor, times(2)).submit(any<Runnable>())
  }

  private fun getStrategy(recorder: ScreenshotRecorder): ScreenshotStrategy {
    val strategyField = ScreenshotRecorder::class.java.getDeclaredField("screenshotStrategy")
    strategyField.isAccessible = true
    return strategyField.get(recorder) as ScreenshotStrategy
  }
}

private class RecorderActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(
      FrameLayout(this).apply {
        layoutParams =
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          )
      }
    )
  }
}
