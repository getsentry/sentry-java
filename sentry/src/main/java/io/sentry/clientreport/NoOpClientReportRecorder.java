package io.sentry.clientreport;

import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.transport.DataCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpClientReportRecorder implements IClientReportRecorder {
  @Override
  public void recordLostEnvelope(@NotNull DiscardReason reason, @Nullable SentryEnvelope envelope) {
    // do nothing
  }

  @Override
  public void recordLostEnvelopeItem(
      @NotNull DiscardReason reason, @Nullable SentryEnvelopeItem envelopeItem) {
    // do nothing
  }

  @Override
  public void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category) {
    // do nothing
  }

  @Override
  public @NotNull SentryEnvelope attachReportToEnvelope(@NotNull SentryEnvelope envelope) {
    return envelope;
  }
}
