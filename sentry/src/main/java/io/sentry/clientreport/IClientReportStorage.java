package io.sentry.clientreport;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface IClientReportStorage {
  void addCount(ClientReportKey key, Long count);

  List<DiscardedEvent> resetCountsAndGet();
}
