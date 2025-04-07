package io.sentry.android.core.internal.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import androidx.annotation.Nullable;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.util.thread.IMainThreadChecker;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ScreenshotUtils {

  private static final long CAPTURE_TIMEOUT_MS = 1000;

  // Used by Hybrid SDKs
  /**
   * @noinspection unused
   */
  public static @Nullable byte[] takeScreenshot(
      final @NotNull Activity activity,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    return takeScreenshot(
        activity, AndroidMainThreadChecker.getInstance(), logger, buildInfoProvider);
  }

  // Used by Hybrid SDKs
  @SuppressLint("NewApi")
  public static @Nullable byte[] takeScreenshot(
      final @NotNull Activity activity,
      final @NotNull IMainThreadChecker mainThreadChecker,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {

    final @Nullable Bitmap screenshot =
        captureScreenshot(activity, mainThreadChecker, logger, buildInfoProvider);
    return compressBitmapToPng(screenshot, logger);
  }

  public static @Nullable Bitmap captureScreenshot(
      final @NotNull Activity activity,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    return captureScreenshot(
        activity, AndroidMainThreadChecker.getInstance(), logger, buildInfoProvider);
  }

  @SuppressLint("NewApi")
  public static @Nullable Bitmap captureScreenshot(
      final @NotNull Activity activity,
      final @NotNull IMainThreadChecker mainThreadChecker,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    // We are keeping BuildInfoProvider param for compatibility, as it's being used by
    // cross-platform SDKs

    if (!isActivityValid(activity)) {
      logger.log(SentryLevel.DEBUG, "Activity isn't valid, not taking screenshot.");
      return null;
    }

    final @Nullable Window window = activity.getWindow();
    if (window == null) {
      logger.log(SentryLevel.DEBUG, "Activity window is null, not taking screenshot.");
      return null;
    }

    final @Nullable View decorView = window.peekDecorView();
    if (decorView == null) {
      logger.log(SentryLevel.DEBUG, "DecorView is null, not taking screenshot.");
      return null;
    }

    final @Nullable View view = decorView.getRootView();
    if (view == null) {
      logger.log(SentryLevel.DEBUG, "Root view is null, not taking screenshot.");
      return null;
    }

    if (view.getWidth() <= 0 || view.getHeight() <= 0) {
      logger.log(SentryLevel.DEBUG, "View's width and height is zeroed, not taking screenshot.");
      return null;
    }

    try {
      // ARGB_8888 -> This configuration is very flexible and offers the best quality
      final Bitmap bitmap =
          Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

      final @NotNull CountDownLatch latch = new CountDownLatch(1);

      // Use Pixel Copy API on new devices, fallback to canvas rendering on older ones
      if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.O) {

        final HandlerThread thread = new HandlerThread("SentryScreenshot");
        thread.start();

        boolean success = false;
        try {
          final Handler handler = new Handler(thread.getLooper());
          final AtomicBoolean copyResultSuccess = new AtomicBoolean(false);

          PixelCopy.request(
              window,
              bitmap,
              copyResult -> {
                copyResultSuccess.set(copyResult == PixelCopy.SUCCESS);
                latch.countDown();
              },
              handler);

          success =
              latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS) && copyResultSuccess.get();
        } catch (Throwable e) {
          // ignored
          logger.log(SentryLevel.ERROR, "Taking screenshot using PixelCopy failed.", e);
        } finally {
          thread.quit();
        }

        if (!success) {
          return null;
        }
      } else {
        final Canvas canvas = new Canvas(bitmap);
        if (mainThreadChecker.isMainThread()) {
          view.draw(canvas);
          latch.countDown();
        } else {
          activity.runOnUiThread(
              () -> {
                try {
                  view.draw(canvas);
                } catch (Throwable e) {
                  logger.log(SentryLevel.ERROR, "Taking screenshot failed (view.draw).", e);
                } finally {
                  latch.countDown();
                }
              });
        }

        if (!latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          return null;
        }
      }
      return bitmap;
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Taking screenshot failed.", e);
    }
    return null;
  }

  /**
   * Compresses the supplied Bitmap to a PNG byte array. After compression, the Bitmap will be
   * recycled.
   *
   * @param bitmap The bitmap to compress
   * @param logger the logger
   * @return the Bitmap in PNG format, or null if the bitmap was null, recycled or compressing faile
   */
  public static @Nullable byte[] compressBitmapToPng(
      final @Nullable Bitmap bitmap, final @NotNull ILogger logger) {
    if (bitmap == null || bitmap.isRecycled()) {
      return null;
    }
    try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      // 0 meaning compress for small size, 100 meaning compress for max quality.
      // Some formats, like PNG which is lossless, will ignore the quality setting.
      bitmap.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);
      bitmap.recycle();

      if (byteArrayOutputStream.size() <= 0) {
        logger.log(SentryLevel.DEBUG, "Screenshot is 0 bytes, not attaching the image.");
        return null;
      }

      // screenshot png is around ~100-150 kb
      return byteArrayOutputStream.toByteArray();
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Compressing bitmap failed.", e);
    }
    return null;
  }

  private static boolean isActivityValid(final @NotNull Activity activity) {
    return !activity.isFinishing() && !activity.isDestroyed();
  }
}
