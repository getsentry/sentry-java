package io.sentry;

import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IFeedbackApi {

  void show();

  void show(final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator);

  void show(
      final @Nullable SentryId associatedEventId,
      final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator);

  /**
   * Enables showing the feedback form when a shake gesture is detected, overriding {@link
   * SentryFeedbackOptions#isUseShakeGesture()}. Only supported on Android; no-op on other
   * platforms.
   */
  void enableOnShake();

  /**
   * Disables showing the feedback form when a shake gesture is detected, overriding {@link
   * SentryFeedbackOptions#isUseShakeGesture()}. Only supported on Android; no-op on other
   * platforms.
   *
   * <p>This turns off the SDK-wide shake detection only. A feedback form whose own options enable
   * the shake gesture still runs its own detection while it is showing and while its host activity
   * is alive, so shaking can re-open that form.
   */
  void disableOnShake();

  /**
   * Whether showing the feedback form on a shake gesture is currently enabled. Always {@code false}
   * on non-Android platforms. Reflects the SDK-wide shake detection only; a form running its own
   * shake detection is not covered by this.
   *
   * @return true if the feedback form is shown when a shake gesture is detected
   */
  boolean isOnShakeEnabled();

  @NotNull
  SentryId capture(final @NotNull Feedback feedback);

  @NotNull
  SentryId capture(final @NotNull Feedback feedback, final @Nullable Hint hint);

  @NotNull
  SentryId capture(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback callback);
}
