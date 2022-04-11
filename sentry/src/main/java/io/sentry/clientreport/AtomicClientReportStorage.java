package io.sentry.clientreport;

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
    final Map<ClientReportKey, AtomicLong> modifyableEventCountsForInit = new ConcurrentHashMap<>();

    for (final DiscardReason discardReason : DiscardReason.values()) {
      for (final DataCategory category : DataCategory.values()) {
        modifyableEventCountsForInit.put(
            new ClientReportKey(discardReason.getReason(), category.getCategory()),
            new AtomicLong(0));
      }
    }

    lostEventCounts = Collections.unmodifiableMap(modifyableEventCountsForInit);
  }

  @Override
  public void addCount(ClientReportKey key, Long count) {
    final @Nullable AtomicLong quantity = lostEventCounts.get(key);

    if (quantity != null) {
      quantity.addAndGet(count);
    }
  }

  @Override
  public List<DiscardedEvent> resetCountsAndGet() {
    final List<DiscardedEvent> discardedEvents = new ArrayList<>(lostEventCounts.size());

    for (final Map.Entry<ClientReportKey, AtomicLong> entry : lostEventCounts.entrySet()) {
      final Long quantity = entry.getValue().getAndSet(0);
      if (quantity > 0) {
        discardedEvents.add(
            new DiscardedEvent(entry.getKey().getReason(), entry.getKey().getCategory(), quantity));
      }
    }
    return discardedEvents;
  }
}
