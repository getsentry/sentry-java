package io.sentry.android.core.internal.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryScreenshotOptions;
import io.sentry.android.replay.util.MaskRenderer;
import io.sentry.android.replay.util.ViewsKt;
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode;
import io.sentry.util.ExceptionUtils;
import io.sentry.util.thread.IThreadChecker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Applies screenshot masking using the Session Replay view hierarchy approach. Requires the
 * `sentry-android-replay` module at runtime, callers must check for its availability first.
 */
@ApiStatus.Internal
public final class ScreenshotMasking {

  private static final long MASKING_TIMEOUT_MS = 2000;

  private ScreenshotMasking() {}

  /**
   * Masks the given screenshot according to the screenshot options.
   *
   * @return the masked bitmap, or null if masking failed. The passed screenshot is recycled on
   *     failure, so a raw screenshot is never returned in place of a masked one.
   */
  public static @Nullable Bitmap mask(
      final @NotNull Activity activity,
      final @NotNull Bitmap screenshot,
      final @NotNull SentryScreenshotOptions screenshotOptions,
      final @NotNull IThreadChecker threadChecker,
      final @NotNull ILogger logger) {
    final @Nullable ViewHierarchyNode rootNode =
        captureViewHierarchy(activity, screenshotOptions, threadChecker, logger);
    if (rootNode == null) {
      screenshot.recycle();
      return null;
    }
    // applyMasking already recycles its bitmaps on failure
    return applyMasking(screenshot, rootNode, logger);
  }

  /**
   * Captures the view hierarchy on the main thread, since view traversal requires it. If already on
   * the main thread, captures directly; otherwise posts to the main thread and waits.
   */
  private static @Nullable ViewHierarchyNode captureViewHierarchy(
      final @NotNull Activity activity,
      final @NotNull SentryScreenshotOptions screenshotOptions,
      final @NotNull IThreadChecker threadChecker,
      final @NotNull ILogger logger) {
    if (threadChecker.isMainThread()) {
      return buildViewHierarchy(activity, screenshotOptions, logger);
    }

    final AtomicReference<ViewHierarchyNode> result = new AtomicReference<>(null);
    final CountDownLatch latch = new CountDownLatch(1);

    try {
      activity.runOnUiThread(
          () -> {
            try {
              result.set(buildViewHierarchy(activity, screenshotOptions, logger));
            } finally {
              latch.countDown();
            }
          });

      if (!latch.await(MASKING_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        logger.log(
            SentryLevel.WARNING, "Timed out waiting for view hierarchy capture on main thread");
        return null;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.log(SentryLevel.ERROR, "Interrupted while capturing view hierarchy", e);
      return null;
    }

    return result.get();
  }

  private static @Nullable ViewHierarchyNode buildViewHierarchy(
      final @NotNull Activity activity,
      final @NotNull SentryScreenshotOptions screenshotOptions,
      final @NotNull ILogger logger) {
    try {
      final @Nullable View rootView =
          activity.getWindow() != null
                  && activity.getWindow().peekDecorView() != null
                  && activity.getWindow().peekDecorView().getRootView() != null
              ? activity.getWindow().peekDecorView().getRootView()
              : null;
      if (rootView == null) {
        return null;
      }

      final ViewHierarchyNode rootNode =
          ViewHierarchyNode.Companion.fromView(rootView, null, 0, screenshotOptions);
      ViewsKt.traverse(rootView, rootNode, screenshotOptions, logger, null);
      return rootNode;
    } catch (Throwable e) {
      // Traversal calls into arbitrary (custom) View code, which can throw anything
      ExceptionUtils.rethrowIfFatal(e);
      logger.log(SentryLevel.ERROR, "Failed to build view hierarchy", e);
      return null;
    }
  }

  private static @Nullable Bitmap applyMasking(
      final @NotNull Bitmap screenshot,
      final @NotNull ViewHierarchyNode rootNode,
      final @NotNull ILogger logger) {
    Bitmap mutableBitmap = screenshot;
    boolean createdCopy = false;
    try (final MaskRenderer maskRenderer = new MaskRenderer()) {
      // Make bitmap mutable if needed
      if (!screenshot.isMutable()) {
        mutableBitmap = screenshot.copy(Bitmap.Config.RGB_565, true);
        if (mutableBitmap == null) {
          screenshot.recycle();
          return null;
        }
        createdCopy = true;
      }

      maskRenderer.renderMasks(mutableBitmap, rootNode, null);

      // Recycle original if we created a copy
      if (createdCopy && !screenshot.isRecycled()) {
        screenshot.recycle();
      }

      return mutableBitmap;
    } catch (RuntimeException e) {
      logger.log(SentryLevel.ERROR, "Failed to mask screenshot", e);
      if (createdCopy) {
        if (!mutableBitmap.isRecycled()) {
          mutableBitmap.recycle();
        }
      }
      if (!screenshot.isRecycled()) {
        screenshot.recycle();
      }
      return null;
    }
  }
}
