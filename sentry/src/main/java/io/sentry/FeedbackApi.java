package io.sentry;

import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FeedbackApi implements IFeedbackApi {

  private final @NotNull IScopes scopes;

  FeedbackApi(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void showForm() {
    showForm(null, null);
  }

  @Override
  public void showForm(final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {
    showForm(null, configurator);
  }

  @Override
  public void showForm(
      final @Nullable SentryId associatedEventId,
      final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {
    final @NotNull SentryOptions options = scopes.getOptions();
    options.getFeedbackOptions().getFormHandler().showForm(associatedEventId, configurator);
  }

  @Override
  public @NotNull SentryId capture(final @NotNull Feedback feedback) {
    return scopes.captureFeedback(feedback);
  }

  @Override
  public @NotNull SentryId capture(final @NotNull Feedback feedback, final @Nullable Hint hint) {
    return scopes.captureFeedback(feedback, hint);
  }

  @Override
  public @NotNull SentryId capture(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback callback) {
    return scopes.captureFeedback(feedback, hint, callback);
  }
}
