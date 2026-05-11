package io.sentry;

import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpFeedbackApi implements IFeedbackApi {

  private static final NoOpFeedbackApi instance = new NoOpFeedbackApi();

  private NoOpFeedbackApi() {}

  public static NoOpFeedbackApi getInstance() {
    return instance;
  }

  @Override
  public void show() {}

  @Override
  public void show(final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {}

  @Override
  public void show(
      final @Nullable SentryId associatedEventId,
      final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {}

  @Override
  public @NotNull SentryId capture(final @NotNull Feedback feedback) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId capture(final @NotNull Feedback feedback, final @Nullable Hint hint) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId capture(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback callback) {
    return SentryId.EMPTY_ID;
  }
}
