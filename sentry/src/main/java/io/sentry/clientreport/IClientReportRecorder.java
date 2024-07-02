package io.sentry.clientreport;

import io.sentry.DataCategory;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IClientReportRecorder {
  void recordLostEnvelope(@NotNull DiscardReason reason, @Nullable SentryEnvelope envelope);

  void recordLostEnvelopeItem(
      @NotNull DiscardReason reason, @Nullable SentryEnvelopeItem envelopeItem);

  void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category);

  void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category, long count);

  @NotNull
  SentryEnvelope attachReportToEnvelope(@NotNull SentryEnvelope envelope);
}
