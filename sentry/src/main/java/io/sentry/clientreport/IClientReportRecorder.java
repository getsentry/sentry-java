package io.sentry.clientreport;

import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.transport.DataCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IClientReportRecorder {
  void recordLostEnvelope(@NotNull DiscardReason reason, @Nullable SentryEnvelope envelope);

  void recordLostEnvelopeItem(
      @NotNull DiscardReason reason, @Nullable SentryEnvelopeItem envelopeItem);

  void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category);

  @NotNull
  SentryEnvelope attachReportToEnvelope(@NotNull SentryEnvelope envelope);
}
