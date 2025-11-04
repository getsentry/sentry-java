package io.sentry.android.replay.screenshot

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.ExecutorProvider
import io.sentry.android.replay.ScreenshotRecorderCallback
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.DebugOverlayDrawable
import io.sentry.android.replay.util.MainLooperHandler
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPixelCopy

@Config(shadows = [ShadowPixelCopy::class], sdk = [30])
@RunWith(AndroidJUnit4::class)
class PixelCopyStrategyTest {

  private class Fixture {
    val options = SentryOptions()
    val callback = mock<ScreenshotRecorderCallback>()
    val debugOverlayDrawable = mock<DebugOverlayDrawable>()
    val config = ScreenshotRecorderConfig(100, 100, 1f, 1f, 1, 1000)

    fun getSut(executor: ScheduledExecutorService = mock()): PixelCopyStrategy {
      return PixelCopyStrategy(
        object : ExecutorProvider {
          override fun getExecutor(): ScheduledExecutorService = executor

          override fun getMainLooperHandler(): MainLooperHandler = MainLooperHandler()
        },
        callback,
        options,
        config,
        debugOverlayDrawable,
      )
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setup() {
    System.setProperty("robolectric.areWindowsMarkedVisible", "true")
    System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
  }

  @Test
  fun `when strategy is closed, lastCaptureSuccessful returns false`() {
    val strategy = fixture.getSut()

    strategy.close()

    assertFalse(strategy.lastCaptureSuccessful())
  }

  @Test
  fun `when close is called while executor task is running, does not crash with recycled bitmap`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()

    var strategy: PixelCopyStrategy? = null

    val failure = AtomicReference<Throwable>()
    // Custom executor that closes the strategy before executing tasks
    val executorThatClosesFirst = mock<ScheduledExecutorService>()
    whenever(executorThatClosesFirst.submit(any<Runnable>())).doAnswer {
      val task = it.getArgument<Runnable>(0)
      strategy?.close()
      try {
        task.run()
      } catch (e: Throwable) {
        // PixelCopyStrategy swallows the exception, so we have to capture it here and rethrow later
        failure.set(e)
      }
      null
    }

    strategy = fixture.getSut(executor = executorThatClosesFirst)
    strategy.capture(activity.get().findViewById(android.R.id.content))
    shadowOf(Looper.getMainLooper()).idle()

    if (failure.get() != null) throw failure.get()
  }
}

private class SimpleActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val linearLayout =
      LinearLayout(this).apply {
        setBackgroundColor(android.R.color.white)
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      }

    val textView =
      TextView(this).apply {
        text = "Hello, World!"
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      }
    linearLayout.addView(textView)

    setContentView(linearLayout)
  }
}
