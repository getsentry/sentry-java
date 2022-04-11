package io.sentry.clientreport;

import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryOptions;
import io.sentry.transport.DataCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpClientReportRecorder implements IClientReportRecorder {
  @Override
  public void recordLostEnvelope(
      @NotNull DiscardReason reason,
      @Nullable SentryEnvelope envelope,
      @NotNull SentryOptions options) {
    // do nothing
  }

  @Override
  public void recordLostEnvelopeItem(
      @NotNull DiscardReason reason,
      @Nullable SentryEnvelopeItem envelopeItem,
      @NotNull SentryOptions options) {
    // do nothing
  }

  @Override
  public void recordLostEvent(
      @NotNull DiscardReason reason,
      @NotNull DataCategory category,
      @NotNull SentryOptions options) {
    // do nothing
  }

  @Override
  public @NotNull SentryEnvelope attachReportToEnvelope(
      @NotNull SentryEnvelope envelope, @NotNull SentryOptions options) {
    return envelope;
  }

  @Override
  public void debug(@NotNull SentryOptions options) {
    // do nothing
  }
}
