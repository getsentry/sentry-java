package io.sentry.clientreport;

import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryOptions;
import io.sentry.transport.DataCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ClientReportRecorder {
  void recordLostEnvelope(
      @NotNull DiscardReason reason,
      @Nullable SentryEnvelope envelope,
      @NotNull SentryOptions options);

  void recordLostEnvelopeItem(
      @NotNull DiscardReason reason,
      @Nullable SentryEnvelopeItem envelopeItem,
      @NotNull SentryOptions options);

  void recordLostEvent(
      @NotNull DiscardReason reason,
      @NotNull DataCategory category,
      @NotNull SentryOptions options);

  @NotNull
  SentryEnvelope attachReportToEnvelope(
      @NotNull SentryEnvelope envelope, @NotNull SentryOptions options);

  void debug(@NotNull SentryOptions options);
}
