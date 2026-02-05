package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;
import static io.sentry.android.core.internal.util.ScreenshotUtils.captureScreenshot;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.android.core.internal.util.Debouncer;
import io.sentry.android.core.internal.util.ScreenshotUtils;
import io.sentry.android.replay.util.MaskRenderer;
import io.sentry.android.replay.util.ViewsKt;
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode;
import io.sentry.protocol.SentryTransaction;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ScreenshotEventProcessor responsible for taking a screenshot of the screen when an error is
 * captured.
 */
@ApiStatus.Internal
public final class ScreenshotEventProcessor implements EventProcessor, Closeable {

  private final @NotNull SentryAndroidOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;

  private final @NotNull Debouncer debouncer;
  private static final long DEBOUNCE_WAIT_TIME_MS = 2000;
  private static final int DEBOUNCE_MAX_EXECUTIONS = 3;

  private @Nullable MaskRenderer maskRenderer = null;

  public ScreenshotEventProcessor(
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final boolean isReplayAvailable) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.debouncer =
        new Debouncer(
            AndroidCurrentDateProvider.getInstance(),
            DEBOUNCE_WAIT_TIME_MS,
            DEBOUNCE_MAX_EXECUTIONS);

    if (isReplayAvailable) {
      maskRenderer = new MaskRenderer();
    }

    if (options.isAttachScreenshot()) {
      addIntegrationToSdkVersion("Screenshot");
    }
  }

  private boolean isMaskingEnabled() {
    if (options.getScreenshotOptions().getMaskViewClasses().isEmpty()) {
      return false;
    }
    if (maskRenderer == null) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Screenshot masking requires sentry-android-replay module");
      return false;
    }
    return true;
  }

  @Override
  public @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @NotNull Hint hint) {
    // that's only necessary because on newer versions of Unity, if not overriding this method, it's
    // throwing 'java.lang.AbstractMethodError: abstract method' and the reason is probably
    // compilation mismatch
    return transaction;
  }

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    if (!event.isErrored()) {
      return event;
    }
    if (!options.isAttachScreenshot()) {
      this.options.getLogger().log(SentryLevel.DEBUG, "attachScreenshot is disabled.");

      return event;
    }
    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity == null || HintUtils.isFromHybridSdk(hint)) {
      return event;
    }

    // skip capturing in case of debouncing (=too many frequent capture requests)
    // the BeforeCaptureCallback may overrules the debouncing decision
    final boolean shouldDebounce = debouncer.checkForDebounce();
    final @Nullable SentryAndroidOptions.BeforeCaptureCallback beforeCaptureCallback =
        options.getBeforeScreenshotCaptureCallback();
    if (beforeCaptureCallback != null) {
      if (!beforeCaptureCallback.execute(event, hint, shouldDebounce)) {
        return event;
      }
    } else if (shouldDebounce) {
      return event;
    }

    Bitmap screenshot =
        captureScreenshot(
            activity, options.getThreadChecker(), options.getLogger(), buildInfoProvider);
    if (screenshot == null) {
      return event;
    }

    // Apply masking if enabled and replay module is available
    if (isMaskingEnabled()) {
      final @Nullable View rootView =
          activity.getWindow() != null
                  && activity.getWindow().getDecorView() != null
                  && activity.getWindow().getDecorView().getRootView() != null
              ? activity.getWindow().getDecorView().getRootView()
              : null;
      if (rootView != null) {
        screenshot = applyMasking(screenshot, rootView);
      }
    }

    final Bitmap finalScreenshot = screenshot;
    hint.setScreenshot(
        Attachment.fromByteProvider(
            () -> ScreenshotUtils.compressBitmapToPng(finalScreenshot, options.getLogger()),
            "screenshot.png",
            "image/png",
            false));
    hint.set(ANDROID_ACTIVITY, activity);
    return event;
  }

  private @NotNull Bitmap applyMasking(
      final @NotNull Bitmap screenshot, final @NotNull View rootView) {
    try {
      // Make bitmap mutable if needed
      Bitmap mutableBitmap = screenshot;
      if (!screenshot.isMutable()) {
        mutableBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, true);
        if (mutableBitmap == null) {
          return screenshot;
        }
      }

      // we can access it here, since it's "internal" only for Kotlin

      // Build view hierarchy and apply masks
      final ViewHierarchyNode rootNode =
          ViewHierarchyNode.Companion.fromView(rootView, null, 0, options.getScreenshotOptions());
      ViewsKt.traverse(rootView, rootNode, options.getScreenshotOptions(), options.getLogger());

      if (maskRenderer != null) {
        maskRenderer.renderMasks(mutableBitmap, rootNode, null);
      }

      // Recycle original if we created a copy
      if (mutableBitmap != screenshot && !screenshot.isRecycled()) {
        screenshot.recycle();
      }

      return mutableBitmap;
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to mask screenshot", e);
      return screenshot;
    }
  }

  @Override
  public @Nullable Long getOrder() {
    return 10000L;
  }

  @Override
  public void close() throws IOException {
    if (maskRenderer != null) {
      maskRenderer.close();
    }
  }
}
