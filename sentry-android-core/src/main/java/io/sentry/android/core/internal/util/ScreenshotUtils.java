package io.sentry.android.core.internal.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import androidx.annotation.Nullable;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.util.thread.IMainThreadChecker;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ScreenshotUtils {

  private static final long CAPTURE_TIMEOUT_MS = 1000;

  public static @Nullable byte[] takeScreenshot(
      final @NotNull Activity activity, final @NotNull ILogger logger) {
    return takeScreenshot(activity, AndroidMainThreadChecker.getInstance(), logger);
  }

  public static @Nullable byte[] takeScreenshot(
      final @NotNull Activity activity,
      final @NotNull IMainThreadChecker mainThreadChecker,
      final @NotNull ILogger logger) {

    if (!isActivityValid(activity)
        || activity.getWindow() == null
        || activity.getWindow().getDecorView() == null
        || activity.getWindow().getDecorView().getRootView() == null) {
      logger.log(SentryLevel.DEBUG, "Activity isn't valid, not taking screenshot.");
      return null;
    }

    final View view = activity.getWindow().getDecorView().getRootView();
    if (view.getWidth() <= 0 || view.getHeight() <= 0) {
      logger.log(SentryLevel.DEBUG, "View's width and height is zeroed, not taking screenshot.");
      return null;
    }

    try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      // ARGB_8888 -> This configuration is very flexible and offers the best quality
      final Bitmap bitmap =
          Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

      final Canvas canvas = new Canvas(bitmap);
      if (mainThreadChecker.isMainThread()) {
        view.draw(canvas);
      } else {
        final @NotNull CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(
            () -> {
              try {
                view.draw(canvas);
                latch.countDown();
              } catch (Throwable e) {
                logger.log(SentryLevel.ERROR, "Taking screenshot failed (view.draw).", e);
              }
            });
        if (!latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          return null;
        }
      }

      // 0 meaning compress for small size, 100 meaning compress for max quality.
      // Some formats, like PNG which is lossless, will ignore the quality setting.
      bitmap.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);

      if (byteArrayOutputStream.size() <= 0) {
        logger.log(SentryLevel.DEBUG, "Screenshot is 0 bytes, not attaching the image.");
        return null;
      }

      // screenshot png is around ~100-150 kb
      return byteArrayOutputStream.toByteArray();
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "Taking screenshot failed.", e);
    }
    return null;
  }

  private static boolean isActivityValid(final @NotNull Activity activity) {
    return !activity.isFinishing() && !activity.isDestroyed();
  }
}
