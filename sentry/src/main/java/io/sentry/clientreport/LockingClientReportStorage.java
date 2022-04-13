package io.sentry.clientreport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

final class LockingClientReportStorage implements IClientReportStorage {

  private final ConcurrentHashMap<ClientReportKey, Long> lostEventCounts =
      new ConcurrentHashMap<>();

  @Override
  public synchronized void addCount(ClientReportKey key, Long count) {
    final @Nullable Long oldValue = lostEventCounts.get(key);

    if (oldValue == null) {
      lostEventCounts.put(key, count);
    } else {
      lostEventCounts.put(key, oldValue + count);
    }
  }

  @Override
  public synchronized List<DiscardedEvent> resetCountsAndGet() {
    final List<DiscardedEvent> discardedEvents = new ArrayList<>(lostEventCounts.size());

    for (final Map.Entry<ClientReportKey, Long> entry : lostEventCounts.entrySet()) {
      discardedEvents.add(
          new DiscardedEvent(
              entry.getKey().getReason(), entry.getKey().getCategory(), entry.getValue()));
    }

    lostEventCounts.clear();

    return discardedEvents;
  }
}
