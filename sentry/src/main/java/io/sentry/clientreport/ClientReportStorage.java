package io.sentry.clientreport;

import io.sentry.SentryOptions;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface ClientReportStorage {
  void addCount(ClientReportKey key, Long count);

  List<DiscardedEvent> resetCountsAndGet();

  void debug(@NotNull SentryOptions options);
}
