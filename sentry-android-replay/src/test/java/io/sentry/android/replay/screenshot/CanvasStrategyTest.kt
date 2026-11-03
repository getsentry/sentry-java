package io.sentry.android.replay.screenshot

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.SentryOptions
import io.sentry.android.replay.ExecutorProvider
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.MainLooperHandler
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], shadows = [DeferredSurfacePixelCopyShadow::class])
class CanvasStrategyTest {

  private class Fixture {
    val options = SentryOptions()
    val config = ScreenshotRecorderConfig(100, 100, 1f, 1f, 1, 1000)

    fun getSut(): CanvasStrategy =
      CanvasStrategy(
        object : ExecutorProvider {
          override fun getExecutor(): ScheduledExecutorService = mock()

          override fun getMainLooperHandler(): MainLooperHandler = MainLooperHandler()

          // runs the render task, the PixelCopy callback and the cleanup, like production
          override fun getBackgroundHandler(): Handler = Handler(Looper.getMainLooper())
        },
        mock(),
        options,
        config,
      )
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setup() {
    DeferredSurfacePixelCopyShadow.reset()
  }

  @Test
  fun `close keeps the screenshot alive until the in-flight pixel copy finishes`() {
    val strategy = fixture.getSut()
    strategy.capture(View(ApplicationProvider.getApplicationContext()))
    shadowOf(Looper.getMainLooper()).idle()
    val screenshot = DeferredSurfacePixelCopyShadow.dest!!

    strategy.close()
    shadowOf(Looper.getMainLooper()).idle()

    // the copy is still writing into the screenshot, recycling it now would abort the RenderThread
    assertThat(screenshot.isRecycled).isFalse()

    DeferredSurfacePixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(screenshot.isRecycled).isTrue()
  }

  @Test
  fun `close recycles the screenshot right away when no pixel copy is in flight`() {
    val strategy = fixture.getSut()
    strategy.capture(View(ApplicationProvider.getApplicationContext()))
    shadowOf(Looper.getMainLooper()).idle()
    val screenshot = DeferredSurfacePixelCopyShadow.dest!!
    DeferredSurfacePixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    strategy.close()
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(screenshot.isRecycled).isTrue()
  }
}

/** Holds back the result of a [PixelCopy] from a [Surface] until [flush] is called. */
@Implements(PixelCopy::class)
class DeferredSurfacePixelCopyShadow {
  companion object {
    private val pendingCallbacks = mutableListOf<() -> Unit>()
    var dest: Bitmap? = null

    fun reset() {
      pendingCallbacks.clear()
      dest = null
    }

    fun flush() {
      val callbacks = pendingCallbacks.toList()
      pendingCallbacks.clear()
      callbacks.forEach { it.invoke() }
    }

    @JvmStatic
    @Implementation
    @Suppress("UNUSED_PARAMETER")
    fun request(
      source: Surface,
      dest: Bitmap,
      listener: PixelCopy.OnPixelCopyFinishedListener,
      listenerThread: Handler,
    ) {
      this.dest = dest
      pendingCallbacks.add {
        listenerThread.post { listener.onPixelCopyFinished(PixelCopy.SUCCESS) }
      }
    }
  }
}
