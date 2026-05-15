package io.sentry.android.replay.screenshot

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.widget.FrameLayout
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPixelCopy

@Config(shadows = [ShadowPixelCopy::class], sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class PixelCopyStrategyTest {

  private class Fixture {
    val options = SentryOptions()
    val callback = mock<ScreenshotRecorderCallback>()
    val debugOverlayDrawable = mock<DebugOverlayDrawable>()
    val config = ScreenshotRecorderConfig(100, 100, 1f, 1f, 1, 1000)
    val contentChangedMarked = AtomicBoolean(false)

    fun getSut(executor: ScheduledExecutorService = mock()): PixelCopyStrategy {
      return PixelCopyStrategy(
        object : ExecutorProvider {
          override fun getExecutor(): ScheduledExecutorService = executor

          override fun getMainLooperHandler(): MainLooperHandler = MainLooperHandler()

          override fun getBackgroundHandler(): Handler = mock()
        },
        callback,
        options,
        config,
        debugOverlayDrawable,
        markContentChanged = { contentChangedMarked.set(true) },
      )
    }

    /** Executor mock that runs submitted tasks synchronously on the calling thread. */
    fun inlineExecutor(): ScheduledExecutorService {
      return mock {
        doAnswer {
            (it.arguments[0] as Runnable).run()
            null // submit(Runnable) returns Future<?>; returning Unit breaks the cast
          }
          .whenever(mock)
          .submit(any<Runnable>())
      }
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

  @Test
  fun `capture does not call markContentChanged when option is disabled`() {
    val activity = buildActivity(ActivityWithSurfaceView::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()

    // Default: isCaptureSurfaceViews = false
    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    strategy.capture(activity.get().findViewById(android.R.id.content))
    shadowOf(Looper.getMainLooper()).idle()

    assertFalse(fixture.contentChangedMarked.get())
    assertTrue(strategy.lastCaptureSuccessful())
    verify(fixture.callback).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  fun `capture re-arms contentChanged when option is enabled and SurfaceView is present`() {
    val activity = buildActivity(ActivityWithSurfaceView::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()

    fixture.options.sessionReplay.isCaptureSurfaceViews = true

    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    strategy.capture(activity.get().findViewById(android.R.id.content))
    shadowOf(Looper.getMainLooper()).idle()

    assertTrue(fixture.contentChangedMarked.get())
  }

  @Test
  fun `capture completes when SurfaceView surface is not valid`() {
    // In Robolectric the SurfaceView holder surface is not valid — this exercises the
    // `surfaceView.holder.surface.isValid == false` branch: each SurfaceView skips its
    // PixelCopy and onCaptureComplete still fires, eventually running the compositor and
    // callback.
    val activity = buildActivity(ActivityWithSurfaceView::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    fixture.options.sessionReplay.isCaptureSurfaceViews = true

    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    strategy.capture(activity.get().findViewById(android.R.id.content))
    shadowOf(Looper.getMainLooper()).idle()

    assertTrue(strategy.lastCaptureSuccessful())
    verify(fixture.callback).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  fun `compositeSurfaceViewInto draws source behind existing destination with DST_OVER`() {
    // Destination ("Window capture"): 100x100, opaque red in the top half,
    // fully transparent in the bottom half (the "hole" where the SurfaceView sits).
    val dest = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val destCanvas = Canvas(dest)
    destCanvas.drawColor(Color.RED)
    val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    destCanvas.drawRect(0f, 50f, 100f, 100f, clearPaint)

    // Source ("SurfaceView capture"): 100x50, solid blue — matches the hole.
    val source = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
    source.eraseColor(Color.BLUE)

    val dstOverPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }
    compositeSurfaceViewInto(
      destCanvas = destCanvas,
      destPaint = dstOverPaint,
      tmpSrc = Rect(),
      tmpDst = RectF(),
      sourceBitmap = source,
      sourceX = 0,
      sourceY = 50,
      windowX = 0,
      windowY = 0,
      scaleFactorX = 1f,
      scaleFactorY = 1f,
    )

    // Top region: still red (DST_OVER must not overwrite existing opaque pixels).
    assertEquals(Color.RED, dest.getPixel(50, 10))
    assertEquals(Color.RED, dest.getPixel(50, 49))
    // Bottom region: now blue (source filled the transparent hole).
    assertEquals(Color.BLUE, dest.getPixel(50, 50))
    assertEquals(Color.BLUE, dest.getPixel(99, 99))
  }

  @Test
  fun `compositeSurfaceViewInto respects scale factors and window offset`() {
    // Destination is 50x50 (scaled recording), fully transparent.
    val dest = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
    val destCanvas = Canvas(dest)

    // Source is 40x40, solid green; its on-screen location is (20, 20).
    val source = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    source.eraseColor(Color.GREEN)

    val dstOverPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }
    compositeSurfaceViewInto(
      destCanvas = destCanvas,
      destPaint = dstOverPaint,
      tmpSrc = Rect(),
      tmpDst = RectF(),
      sourceBitmap = source,
      sourceX = 20,
      sourceY = 20,
      windowX = 10, // window is at (10, 10)
      windowY = 10,
      scaleFactorX = 0.5f, // 0.5x scale → destination coords halve
      scaleFactorY = 0.5f,
    )

    // Expected destination rect: ((20-10)*0.5, (20-10)*0.5) = (5, 5), size 40*0.5 = 20x20
    // → occupies pixels [5..25) × [5..25). Check inside, on the edge, and just outside.
    assertEquals(Color.GREEN, dest.getPixel(5, 5))
    assertEquals(Color.GREEN, dest.getPixel(15, 15))
    assertEquals(Color.GREEN, dest.getPixel(24, 24))
    // Just outside the rect — still transparent.
    assertEquals(0, dest.getPixel(4, 4))
    assertEquals(0, dest.getPixel(25, 25))
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

private class ActivityWithSurfaceView : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root =
      FrameLayout(this).apply {
        setBackgroundColor(android.R.color.white)
        layoutParams =
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
          )
      }
    root.addView(
      TextView(this).apply {
        text = "Overlay"
        layoutParams = FrameLayout.LayoutParams(200, 50)
      }
    )
    root.addView(SurfaceView(this).apply { layoutParams = FrameLayout.LayoutParams(200, 200) })
    setContentView(root)
  }
}
