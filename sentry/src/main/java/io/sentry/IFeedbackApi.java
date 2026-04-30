package io.sentry;

import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IFeedbackApi {

  void showForm();

  void showForm(final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator);

  void showForm(
      final @Nullable SentryId associatedEventId,
      final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator);

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
