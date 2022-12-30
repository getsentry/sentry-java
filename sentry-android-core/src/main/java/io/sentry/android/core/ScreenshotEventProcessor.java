package io.sentry.android.core;

import static io.sentry.TypeCheckHint.ANDROID_ACTIVITY;
import static io.sentry.android.core.internal.util.ScreenshotUtils.takeScreenshot;

import android.app.Activity;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * ScreenshotEventProcessor responsible for taking a screenshot of the screen when an error is
 * captured.
 */
@ApiStatus.Internal
public final class ScreenshotEventProcessor implements EventProcessor {

  private final @NotNull SentryAndroidOptions options;
  private final @NotNull BuildInfoProvider buildInfoProvider;

  public ScreenshotEventProcessor(
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "BuildInfoProvider is required");
  }

  @SuppressWarnings("NullAway")
  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, @NotNull Hint hint) {
    if (!event.isErrored()) {
      return event;
    }
    if (!options.isAttachScreenshot()) {
      this.options.getLogger().log(SentryLevel.DEBUG, "attachScreenshot is disabled.");

      return event;
    }
    final Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity == null || HintUtils.isFromHybridSdk(hint)) {
      return event;
    }

    final byte[] screenshot = takeScreenshot(activity, options.getLogger(), buildInfoProvider);
    if (screenshot == null) {
      return event;
    }

    hint.setScreenshot(Attachment.fromScreenshot(screenshot));
    hint.set(ANDROID_ACTIVITY, activity);
    return event;
  }
}
