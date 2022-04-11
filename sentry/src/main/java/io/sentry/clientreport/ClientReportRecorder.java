package io.sentry.clientreport;

import io.sentry.DateUtils;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.transport.DataCategory;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClientReportRecorder implements IClientReportRecorder {

  private static volatile @Nullable ClientReportRecorder SHARED_INSTANCE = null;

  public static @NotNull ClientReportRecorder getInstance() {
    if (SHARED_INSTANCE == null) {
      synchronized (ClientReportRecorder.class) {
        if (SHARED_INSTANCE == null) {
          SHARED_INSTANCE = new ClientReportRecorder();
        }
      }
    }

    return SHARED_INSTANCE;
  }

  private final ClientReportStorage storage;

  private ClientReportRecorder() {
    this.storage = new AtomicClientReportStorage();
  }

  @Override
  public @NotNull SentryEnvelope attachReportToEnvelope(
      @NotNull SentryEnvelope envelope, @NotNull SentryOptions options) {
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
  public void recordLostEnvelope(
      @NotNull DiscardReason reason,
      @Nullable SentryEnvelope envelope,
      @NotNull SentryOptions options) {
    if (envelope == null) {
      return;
    }

    try {
      for (final SentryEnvelopeItem item : envelope.getItems()) {
        recordLostEnvelopeItem(reason, item, options);
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Unable to record lost envelope.");
    }
  }

  @Override
  public void recordLostEnvelopeItem(
      @NotNull DiscardReason reason,
      @Nullable SentryEnvelopeItem envelopeItem,
      @NotNull SentryOptions options) {
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
        recordLostEventInternal(
            reason.getReason(), categoryFromItemType(itemType).getCategory(), 1L);
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Unable to record lost envelope item.");
    }
  }

  @Override
  public void recordLostEvent(
      @NotNull DiscardReason reason,
      @NotNull DataCategory category,
      @NotNull SentryOptions options) {
    try {
      recordLostEventInternal(reason.getReason(), category.getCategory(), 1L);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Unable to record lost event.");
    }
  }

  @Override
  public void debug(@NotNull SentryOptions options) {
    storage.debug(options);
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
    if (SentryItemType.Attachment.equals(itemType)) {
      return DataCategory.Attachment;
    }

    return DataCategory.Default;
  }
}
