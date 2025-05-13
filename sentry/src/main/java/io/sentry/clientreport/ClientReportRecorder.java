package io.sentry.clientreport;

import io.sentry.DataCategory;
import io.sentry.DateUtils;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentrySpan;
import io.sentry.protocol.SentryTransaction;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ClientReportRecorder implements IClientReportRecorder {

  private final @NotNull IClientReportStorage storage;
  private final @NotNull SentryOptions options;

  public ClientReportRecorder(@NotNull SentryOptions options) {
    this.options = options;
    this.storage = new AtomicClientReportStorage();
  }

  @Override
  public @NotNull SentryEnvelope attachReportToEnvelope(@NotNull SentryEnvelope envelope) {
    @Nullable ClientReport clientReport = resetCountsAndGenerateClientReport();
    if (clientReport == null) {
      return envelope;
    }

    try {
      options.getLogger().log(SentryLevel.DEBUG, "Attaching client report to envelope.");

      final List<SentryEnvelopeItem> items = new ArrayList<>();

      for (final SentryEnvelopeItem item : envelope.getItems()) {
        items.add(item);
      }

      items.add(SentryEnvelopeItem.fromClientReport(options.getSerializer(), clientReport));

      return new SentryEnvelope(envelope.getHeader(), items);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Unable to attach client report to envelope.");
      return envelope;
    }
  }

  @Override
  public void recordLostEnvelope(@NotNull DiscardReason reason, @Nullable SentryEnvelope envelope) {
    if (envelope == null) {
      return;
    }

    try {
      for (final SentryEnvelopeItem item : envelope.getItems()) {
        recordLostEnvelopeItem(reason, item);
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Unable to record lost envelope.");
    }
  }

  @Override
  public void recordLostEnvelopeItem(
      @NotNull DiscardReason reason, @Nullable SentryEnvelopeItem envelopeItem) {
    if (envelopeItem == null) {
      return;
    }

    try {
      final @NotNull SentryItemType itemType = envelopeItem.getHeader().getType();
      if (SentryItemType.ClientReport.equals(itemType)) {
        try {
          final ClientReport clientReport = envelopeItem.getClientReport(options.getSerializer());
          restoreCountsFromClientReport(clientReport);
        } catch (Exception e) {
          options
              .getLogger()
              .log(SentryLevel.ERROR, "Unable to restore counts from previous client report.");
        }
      } else {
        final @NotNull DataCategory itemCategory = categoryFromItemType(itemType);
        if (itemCategory.equals(DataCategory.Transaction)) {
          final @Nullable SentryTransaction transaction =
              envelopeItem.getTransaction(options.getSerializer());
          if (transaction != null) {
            final @NotNull List<SentrySpan> spans = transaction.getSpans();
            // When a transaction is dropped, we also record its spans as dropped plus one,
            // since Relay extracts an additional span from the transaction.
            recordLostEventInternal(
                reason.getReason(), DataCategory.Span.getCategory(), spans.size() + 1L);
          }
        }
        recordLostEventInternal(reason.getReason(), itemCategory.getCategory(), 1L);
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Unable to record lost envelope item.");
    }
  }

  @Override
  public void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category) {
    recordLostEvent(reason, category, 1);
  }

  @Override
  public void recordLostEvent(
      @NotNull DiscardReason reason, @NotNull DataCategory category, long count) {
    try {
      recordLostEventInternal(reason.getReason(), category.getCategory(), count);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Unable to record lost event.");
    }
  }

  private void recordLostEventInternal(
      @NotNull String reason, @NotNull String category, @NotNull Long countToAdd) {
    final ClientReportKey key = new ClientReportKey(reason, category);
    storage.addCount(key, countToAdd);
  }

  @Nullable
  ClientReport resetCountsAndGenerateClientReport() {
    final Date currentDate = DateUtils.getCurrentDateTime();
    final List<DiscardedEvent> discardedEvents = storage.resetCountsAndGet();

    if (discardedEvents.isEmpty()) {
      return null;
    } else {
      return new ClientReport(currentDate, discardedEvents);
    }
  }

  private void restoreCountsFromClientReport(@Nullable ClientReport clientReport) {
    if (clientReport == null) {
      return;
    }

    for (final DiscardedEvent discardedEvent : clientReport.getDiscardedEvents()) {
      recordLostEventInternal(
          discardedEvent.getReason(), discardedEvent.getCategory(), discardedEvent.getQuantity());
    }
  }

  private DataCategory categoryFromItemType(SentryItemType itemType) {
    if (SentryItemType.Event.equals(itemType)) {
      return DataCategory.Error;
    }
    if (SentryItemType.Session.equals(itemType)) {
      return DataCategory.Session;
    }
    if (SentryItemType.Transaction.equals(itemType)) {
      return DataCategory.Transaction;
    }
    if (SentryItemType.UserFeedback.equals(itemType)) {
      return DataCategory.UserReport;
    }
    if (SentryItemType.Feedback.equals(itemType)) {
      return DataCategory.Feedback;
    }
    if (SentryItemType.Profile.equals(itemType)) {
      return DataCategory.Profile;
    }
    if (SentryItemType.ProfileChunk.equals(itemType)) {
      return DataCategory.ProfileChunkUi;
    }
    if (SentryItemType.Attachment.equals(itemType)) {
      return DataCategory.Attachment;
    }
    if (SentryItemType.CheckIn.equals(itemType)) {
      return DataCategory.Monitor;
    }
    if (SentryItemType.ReplayVideo.equals(itemType)) {
      return DataCategory.Replay;
    }
    if (SentryItemType.Log.equals(itemType)) {
      return DataCategory.LogItem;
    }

    return DataCategory.Default;
  }
}
