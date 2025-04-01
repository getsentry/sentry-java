package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;
import static io.sentry.android.core.internal.util.ScreenshotUtils.takeScreenshot;
import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.android.core.internal.util.Debouncer;
import io.sentry.protocol.SentryTransaction;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ScreenshotEventProcessor responsible for taking a screenshot of the screen when an error is
 * captured.
 */
@ApiStatus.Internal
public final class ScreenshotEventProcessor implements EventProcessor {

  private final @NotNull SentryAndroidOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;

  private final @NotNull Debouncer debouncer;
  private static final long DEBOUNCE_WAIT_TIME_MS = 2000;
  private static final int DEBOUNCE_MAX_EXECUTIONS = 3;

  public ScreenshotEventProcessor(
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
    this.debouncer =
        new Debouncer(
            AndroidCurrentDateProvider.getInstance(),
            DEBOUNCE_WAIT_TIME_MS,
            DEBOUNCE_MAX_EXECUTIONS);

    if (options.isAttachScreenshot()) {
      addIntegrationToSdkVersion("Screenshot");
    }
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
      if (options.getLogger().isEnabled(SentryLevel.DEBUG)) {
        this.options.getLogger().log(SentryLevel.DEBUG, "attachScreenshot is disabled.");
      }

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

    final byte[] screenshot =
        takeScreenshot(
            activity, options.getThreadChecker(), options.getLogger(), buildInfoProvider);
    if (screenshot == null) {
      return event;
    }

    hint.setScreenshot(Attachment.fromScreenshot(screenshot));
    hint.set(ANDROID_ACTIVITY, activity);
    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 10000L;
  }
}
