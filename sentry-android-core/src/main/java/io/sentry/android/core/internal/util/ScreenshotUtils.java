package io.sentry.android.core.internal.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import androidx.annotation.Nullable;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.util.thread.IThreadChecker;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    return takeScreenshot(activity, AndroidThreadChecker.getInstance(), logger, buildInfoProvider);
  }

  // Used by Hybrid SDKs
  @SuppressLint("NewApi")
  public static @Nullable byte[] takeScreenshot(
      final @NotNull Activity activity,
      final @NotNull IThreadChecker threadChecker,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {

    final @Nullable Bitmap screenshot =
        captureScreenshot(activity, threadChecker, logger, buildInfoProvider);
    return compressBitmapToPng(screenshot, logger);
  }

  // Used by Hybrid SDKs
  /**
   * Takes a screenshot and masks it according to {@link SentryAndroidOptions#getScreenshot()}, the
   * same way {@link io.sentry.android.core.ScreenshotEventProcessor} does for native errors.
   *
   * @see #captureMaskedScreenshot(Activity, SentryAndroidOptions, BuildInfoProvider, boolean)
   * @noinspection unused
   */
  public static @Nullable byte[] takeMaskedScreenshot(
      final @NotNull Activity activity,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    final boolean isReplayAvailable = ScreenshotMasking.isAvailable(options.getLogger());
    return compressBitmapToPng(
        captureMaskedScreenshot(activity, options, buildInfoProvider, isReplayAvailable),
        options.getLogger());
  }

  /**
   * Captures a screenshot and masks it according to {@link SentryAndroidOptions#getScreenshot()}.
   * Shared by {@link io.sentry.android.core.ScreenshotEventProcessor} and Hybrid SDKs, so native
   * and hybrid screenshots follow the same masking rules.
   *
   * <p>If no masking is configured, the screenshot is returned unmasked. If masking is configured
   * but the `sentry-android-replay` module is not available, or masking fails, null is returned to
   * avoid leaking sensitive data.
   */
  public static @Nullable Bitmap captureMaskedScreenshot(
      final @NotNull Activity activity,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final boolean isReplayAvailable) {
    final boolean isMaskingConfigured = !options.getScreenshot().getMaskViewClasses().isEmpty();
    if (isMaskingConfigured && !isReplayAvailable) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Screenshot masking requires sentry-android-replay module");
      return null;
    }

    final @Nullable Bitmap screenshot =
        captureScreenshot(
            activity, options.getThreadChecker(), options.getLogger(), buildInfoProvider);
    if (screenshot == null || !isMaskingConfigured) {
      return screenshot;
    }

    // mask() recycles the screenshot on failure, so a raw screenshot is never returned
    return ScreenshotMasking.mask(
        activity,
        screenshot,
        options.getScreenshot(),
        options.getThreadChecker(),
        options.getLogger());
  }

  public static @Nullable Bitmap captureScreenshot(
      final @NotNull Activity activity,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    return captureScreenshot(
        activity, AndroidThreadChecker.getInstance(), logger, buildInfoProvider);
  }

  @SuppressLint("NewApi")
  public static @Nullable Bitmap captureScreenshot(
      final @NotNull Activity activity,
      final @NotNull IThreadChecker threadChecker,
      final @NotNull ILogger logger,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    // We are keeping the IThreadChecker and BuildInfoProvider params for compatibility, as they are
    // being used by cross-platform SDKs

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
      final Bitmap bitmap =
          Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.RGB_565);

      final @NotNull CountDownLatch latch = new CountDownLatch(1);

      final HandlerThread thread = new HandlerThread("SentryScreenshot");
      thread.start();

      boolean success = false;
      final AtomicInteger copyResultCode = new AtomicInteger(-1);
      try {
        final Handler handler = new Handler(thread.getLooper());

        PixelCopy.request(
            window,
            bitmap,
            copyResult -> {
              copyResultCode.set(copyResult);
              latch.countDown();
            },
            handler);

        final boolean completed = latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        success = completed && copyResultCode.get() == PixelCopy.SUCCESS;
      } catch (Throwable e) {
        // ignored
        logger.log(SentryLevel.ERROR, "Taking screenshot using PixelCopy failed.", e);
      } finally {
        thread.quit();
      }

      if (!success) {
        logger.log(
            SentryLevel.WARNING,
            "PixelCopy failed for screenshot capture (result=%d).",
            copyResultCode.get());
        return null;
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
