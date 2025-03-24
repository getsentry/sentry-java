package io.sentry.clientreport;

import io.sentry.DataCategory;
import io.sentry.util.LazyEvaluator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class AtomicClientReportStorage implements IClientReportStorage {

  private final @NotNull LazyEvaluator<Map<ClientReportKey, AtomicLong>> lostEventCounts =
      new LazyEvaluator<>(
          () -> {
            final Map<ClientReportKey, AtomicLong> modifyableEventCountsForInit =
                new ConcurrentHashMap<>();

            for (final DiscardReason discardReason : DiscardReason.values()) {
              for (final DataCategory category : DataCategory.values()) {
                modifyableEventCountsForInit.put(
                    new ClientReportKey(discardReason.getReason(), category.getCategory()),
                    new AtomicLong(0));
              }
            }

            return Collections.unmodifiableMap(modifyableEventCountsForInit);
          });

  public AtomicClientReportStorage() {}

  @Override
  public void addCount(ClientReportKey key, Long count) {
    final @Nullable AtomicLong quantity = lostEventCounts.getValue().get(key);

    if (quantity != null) {
      quantity.addAndGet(count);
    }
  }

  @Override
  public List<DiscardedEvent> resetCountsAndGet() {
    final List<DiscardedEvent> discardedEvents = new ArrayList<>();

    Set<Map.Entry<ClientReportKey, AtomicLong>> entrySet = lostEventCounts.getValue().entrySet();
    for (final Map.Entry<ClientReportKey, AtomicLong> entry : entrySet) {
      final Long quantity = entry.getValue().getAndSet(0);
      if (quantity > 0) {
        discardedEvents.add(
            new DiscardedEvent(entry.getKey().getReason(), entry.getKey().getCategory(), quantity));
      }
    }
    return discardedEvents;
  }
}
