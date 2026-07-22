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
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.ExecutorProvider
import io.sentry.android.replay.ScreenshotRecorderCallback
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.CompletedFuture
import io.sentry.android.replay.util.DebugOverlayDrawable
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.ReplayRunnable
import java.util.concurrent.Future
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
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
            // Mirror ReplayExecutorService's inline contract: a completed future, not null. Null
            // means "rejected" and would make capture() run its null-fallback finishFrame on top of
            // the task's own, a double-release production never does on the inline path.
            CompletedFuture
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
    DeferredWindowPixelCopyShadow.reset()
  }

  @Test
  fun `when strategy is closed, lastCaptureSuccessful returns false`() {
    val strategy = fixture.getSut()

    strategy.close()

    assertFalse(strategy.lastCaptureSuccessful())
  }

  @Test
  fun `when close races the mask task, masking is skipped and no screenshot is emitted`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()

    var strategy: PixelCopyStrategy? = null

    val failure = AtomicReference<Throwable>()
    // Custom executor that closes the strategy right before running the mask task, to simulate
    // close() racing an in-flight mask task. We key off the mask task specifically (not "the first
    // submit") because close() itself submits the cleanup task — closing again when that runs would
    // recurse via close() -> scheduleCleanup() -> submit(), a loop no real code path can produce.
    val executorThatClosesFirst = mock<ScheduledExecutorService>()
    whenever(executorThatClosesFirst.submit(any<Runnable>())).doAnswer {
      val task = it.getArgument<Runnable>(0)
      if ((task as? ReplayRunnable)?.taskName == "screenshot_recorder.mask") {
        strategy?.close()
      }
      try {
        task.run()
      } catch (e: Throwable) {
        // PixelCopyStrategy swallows the exception, so we have to capture it here and rethrow later
        failure.set(e)
      }
      CompletedFuture
    }

    strategy = fixture.getSut(executor = executorThatClosesFirst)
    strategy.capture(activity.get().findViewById(android.R.id.content))
    shadowOf(Looper.getMainLooper()).idle()

    if (failure.get() != null) throw failure.get()
    // close() landed before masking ran, so applyMaskingAndNotify must bail out early and never
    // hand a screenshot to the callback after the strategy is closed.
    verify(fixture.callback, never()).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `capture drops frame while PixelCopy is in flight`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val strategy = fixture.getSut(executor = fixture.inlineExecutor())

    strategy.capture(root)
    strategy.capture(root)

    assertTrue(fixture.contentChangedMarked.get())

    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(fixture.callback).onScreenshotRecorded(any<Bitmap>())

    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(fixture.callback, times(2)).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `capture drops frame while masking is in flight`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val tasks = mutableListOf<Runnable>()
    val executor = mock<ScheduledExecutorService>()
    whenever(executor.submit(any<Runnable>())).doAnswer {
      tasks += it.getArgument<Runnable>(0)
      mock<Future<*>>()
    }
    val strategy = fixture.getSut(executor)

    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(1, tasks.size)
    tasks.removeAt(0).run()

    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(1, tasks.size)
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `emitLastScreenshot skips while frame is in flight`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    captureStableFrame(strategy, root)

    strategy.capture(root)
    strategy.emitLastScreenshot()

    verify(fixture.callback).onScreenshotRecorded(any<Bitmap>())

    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
    verify(fixture.callback, times(2)).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `emitLastScreenshot holds the frame gate until the emit task drains`() {
    // emit submits the consumer call to the executor so the bitmap read (JPEG compress) runs
    // inline on the worker thread while the gate is held — same pattern as the masked capture path.
    // Invariant: while the emit task is still queued (gate held), a racing capture is dropped.
    // Without the gate (old `if (!frameInFlight.get())`) that capture proceeds -> extra frame.
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val tasks = mutableListOf<Runnable>()
    val executor = mock<ScheduledExecutorService>()
    whenever(executor.submit(any<Runnable>())).doAnswer {
      tasks.add(it.arguments[0] as Runnable)
      mock<Future<*>>()
    }
    val strategy = fixture.getSut(executor)

    // Set up a successful last capture: capture -> queued mask task -> drain releases the gate.
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
    tasks.removeAll {
      it.run()
      true
    }
    verify(fixture.callback, times(1)).onScreenshotRecorded(any<Bitmap>())

    // Emit takes the gate and queues the consumer task (still pending).
    strategy.emitLastScreenshot()
    // Callback hasn't fired yet — the task is queued, not drained.
    verify(fixture.callback, times(1)).onScreenshotRecorded(any<Bitmap>())

    // A capture racing in before the emit task drains must be dropped (gate held).
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
    verify(fixture.callback, times(1)).onScreenshotRecorded(any<Bitmap>())

    // Drain the emit task -> callback fires, gate released -> captures resume.
    tasks.removeAll {
      it.run()
      true
    }
    verify(fixture.callback, times(2)).onScreenshotRecorded(any<Bitmap>())
    captureStableFrame(strategy, root)
    tasks.removeAll {
      it.run()
      true
    }
    verify(fixture.callback, times(3)).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `close defers cleanup until PixelCopy completes`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val executor = mock<ScheduledExecutorService>()
    val strategy = fixture.getSut(executor)

    strategy.capture(root)
    strategy.close()

    verify(executor, never()).submit(any<Runnable>())

    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(executor).submit(any<Runnable>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `close-triggered cleanup keeps the frame gate so a racing capture cannot double-clean up`() {
    // Guards the CAS handoff in finishFrame(). The real race is a 3-thread interleave (a new
    // capture takes the gate the instant finishFrame releases it, then the old finishFrame recycles
    // the bitmap the new capture is writing) and isn't deterministically reproducible single-
    // threaded. This exercises its observable invariant instead: when finishFrame cleans up on
    // close, it must re-take the gate (frameInFlight stays held), so any later capture is dropped
    // rather than sneaking through to schedule a *second* cleanup on the shared screenshot.
    // Without the CAS (plain frameInFlight.set(false)) the gate is left free and the follow-up
    // capture reaches the isClosed guard and schedules cleanup again -> 2 submits.
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val executor = mock<ScheduledExecutorService>()
    whenever(executor.submit(any<Runnable>())).thenReturn(mock<Future<*>>())
    val strategy = fixture.getSut(executor)

    strategy.capture(root)
    strategy.close() // in-flight -> cleanup deferred, no submit yet

    // PixelCopy completes; the callback sees isClosed and runs finishFrame -> the one cleanup.
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    // A capture racing in after close must be dropped (gate still held), not schedule cleanup
    // again.
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(executor, times(1)).submit(any<Runnable>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `idle close claims the gate so a racing capture cannot schedule a second cleanup`() {
    // Mirror of the finishFrame guard, but for close()'s idle path (no frame in flight). close()
    // must atomically claim the gate before scheduling cleanup; otherwise a capture racing in right
    // after the check can take the gate, see isClosed, run finishFrame and schedule cleanup a
    // second
    // time. Both cleanups are idempotent, but a single submit is the invariant we keep uniform.
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val executor = mock<ScheduledExecutorService>()
    whenever(executor.submit(any<Runnable>())).thenReturn(mock<Future<*>>())
    val strategy = fixture.getSut(executor)

    strategy.close() // idle -> claims gate, schedules the one cleanup
    // A capture landing after close must be dropped (gate held), not schedule cleanup again.
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(executor, times(1)).submit(any<Runnable>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `frame gate is released when masking submit is rejected`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    // Simulate an already-shutdown executor: submit returns null.
    val executor = mock<ScheduledExecutorService>()
    whenever(executor.submit(any<Runnable>())).thenReturn(null)
    val strategy = fixture.getSut(executor)

    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    // Gate must have been released; a follow-up capture should proceed rather than being dropped.
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(executor, times(2)).submit(any<Runnable>())
  }

  @Test
  fun `close cleans up inline when executor is already shut down`() {
    // submit returns null → previously the bitmap + maskRenderer would leak.
    val executor = mock<ScheduledExecutorService>()
    whenever(executor.submit(any<Runnable>())).thenReturn(null)
    val strategy = fixture.getSut(executor)

    strategy.close()

    // No crash and the submit was attempted exactly once (cleanup ran inline as fallback).
    verify(executor).submit(any<Runnable>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `capture drops frame while PixelCopy is in flight`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val strategy = fixture.getSut(executor = fixture.inlineExecutor())

    strategy.capture(root)
    strategy.capture(root)

    assertTrue(fixture.contentChangedMarked.get())

    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(fixture.callback).onScreenshotRecorded(any<Bitmap>())

    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(fixture.callback, times(2)).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `capture drops frame while masking is in flight`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val tasks = mutableListOf<Runnable>()
    val executor = mock<ScheduledExecutorService>()
    whenever(executor.submit(any<Runnable>())).doAnswer {
      tasks += it.getArgument<Runnable>(0)
      mock<Future<*>>()
    }
    val strategy = fixture.getSut(executor)

    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(1, tasks.size)
    tasks.removeAt(0).run()

    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(1, tasks.size)
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `emitLastScreenshot skips while frame is in flight`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    captureStableFrame(strategy, root)

    strategy.capture(root)
    strategy.emitLastScreenshot()

    verify(fixture.callback).onScreenshotRecorded(any<Bitmap>())

    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
    verify(fixture.callback, times(2)).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `close defers cleanup until PixelCopy completes`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)
    val executor = mock<ScheduledExecutorService>()
    val strategy = fixture.getSut(executor)

    strategy.capture(root)
    strategy.close()

    verify(executor, never()).submit(any<Runnable>())

    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()

    verify(executor).submit(any<Runnable>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `capture skips the first unstable PixelCopy result`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)

    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    captureUnstableFrame(strategy, root)

    assertFalse(strategy.lastCaptureSuccessful())
    verify(fixture.callback, never()).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `capture emits the second consecutive unstable PixelCopy result`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)

    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    captureUnstableFrame(strategy, root)
    captureUnstableFrame(strategy, root)

    assertTrue(strategy.lastCaptureSuccessful())
    verify(fixture.callback).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `capture keeps emitting after entering continuous instability mode`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)

    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    captureUnstableFrame(strategy, root)
    captureUnstableFrame(strategy, root)
    captureUnstableFrame(strategy, root)

    assertTrue(strategy.lastCaptureSuccessful())
    verify(fixture.callback, times(2)).onScreenshotRecorded(any<Bitmap>())
  }

  @Test
  @Config(shadows = [DeferredWindowPixelCopyShadow::class])
  fun `stable capture resets the unstable PixelCopy counter`() {
    val activity = buildActivity(SimpleActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()
    val root = activity.get().findViewById<View>(android.R.id.content)

    val strategy = fixture.getSut(executor = fixture.inlineExecutor())
    captureUnstableFrame(strategy, root)
    captureUnstableFrame(strategy, root)
    captureStableFrame(strategy, root)
    captureUnstableFrame(strategy, root)

    assertFalse(strategy.lastCaptureSuccessful())
    verify(fixture.callback, times(2)).onScreenshotRecorded(any<Bitmap>())
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

  private fun captureUnstableFrame(strategy: PixelCopyStrategy, root: View) {
    strategy.capture(root)
    strategy.onContentChanged()
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun captureStableFrame(strategy: PixelCopyStrategy, root: View) {
    strategy.capture(root)
    DeferredWindowPixelCopyShadow.flush()
    shadowOf(Looper.getMainLooper()).idle()
  }
}

@Implements(PixelCopy::class)
class DeferredWindowPixelCopyShadow {
  companion object {
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    fun reset() {
      pendingCallbacks.clear()
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
      _source: Window,
      _dest: Bitmap,
      listener: PixelCopy.OnPixelCopyFinishedListener,
      listenerThread: Handler,
    ) {
      pendingCallbacks.add {
        listenerThread.post { listener.onPixelCopyFinished(PixelCopy.SUCCESS) }
      }
    }
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
