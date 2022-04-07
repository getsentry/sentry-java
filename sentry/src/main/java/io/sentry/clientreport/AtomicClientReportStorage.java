package io.sentry.clientreport;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.transport.DataCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AtomicClientReportStorage implements ClientReportStorage {

  private final @NotNull Map<ClientReportKey, AtomicLong> lostEventCounts;

  public AtomicClientReportStorage() {
    Map<ClientReportKey, AtomicLong> map = new ConcurrentHashMap<>();

    for (DiscardReason discardReason : DiscardReason.values()) {
      for (DataCategory category : DataCategory.values()) {
        map.put(
            new ClientReportKey(discardReason.getReason(), category.getCategory()),
            new AtomicLong(0));
      }
    }

    lostEventCounts = Collections.unmodifiableMap(map);
  }

  @Override
  public void addCount(ClientReportKey key, Long count) {
    @Nullable AtomicLong quantity = lostEventCounts.get(key);

    if (quantity != null) {
      quantity.addAndGet(count);
    }
  }

  @Override
  public List<DiscardedEvent> resetCountsAndGet() {
    List<DiscardedEvent> discardedEvents = new ArrayList<>(lostEventCounts.size());

    for (Map.Entry<ClientReportKey, AtomicLong> entry : lostEventCounts.entrySet()) {
      Long quantity = entry.getValue().getAndSet(0);
      if (quantity > 0) {
        discardedEvents.add(
            new DiscardedEvent(entry.getKey().getReason(), entry.getKey().getCategory(), quantity));
      }
    }
    return discardedEvents;
  }

  @Override
  public void debug(@NotNull SentryOptions options) {
    ILogger logger = options.getLogger();
    if (!logger.isEnabled(SentryLevel.DEBUG)) {
      return;
    }

    try {
      logger.log(SentryLevel.DEBUG, "Client report (" + lostEventCounts.size() + " entries)");

      for (Map.Entry<ClientReportKey, AtomicLong> entry : lostEventCounts.entrySet()) {
        logger.log(SentryLevel.DEBUG, entry.getKey() + "= " + entry.getValue());
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, e, "Unable print client report recorder debug info.");
    }
  }
}
