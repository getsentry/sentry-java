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
   */
  void disableOnShake();

  /**
   * Whether showing the feedback form on a shake gesture is currently enabled. Always {@code false}
   * on non-Android platforms.
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
