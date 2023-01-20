package io.sentry.android.core.internal.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import androidx.annotation.Nullable;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.android.core.BuildInfoProvider;
import java.io.ByteArrayOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ScreenshotUtils {
  public static @Nullable byte[] takeScreenshot(
      final @NotNull Activity activity,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    if (!isActivityValid(activity, buildInfoProvider)
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
      view.draw(canvas);

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

  @SuppressLint("NewApi")
  private static boolean isActivityValid(
      final @NotNull Activity activity, final @NotNull BuildInfoProvider buildInfoProvider) {
    if (buildInfoProvider.getSdkInfoVersion() >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return !activity.isFinishing() && !activity.isDestroyed();
    } else {
      return !activity.isFinishing();
    }
  }
}
