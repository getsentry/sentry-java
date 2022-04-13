package io.sentry.clientreport;

import java.util.List;

public interface IClientReportStorage {
  void addCount(ClientReportKey key, Long count);

  List<DiscardedEvent> resetCountsAndGet();
}
