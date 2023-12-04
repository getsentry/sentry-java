package io.sentry;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TransactionPerformanceCollector {

  void start(@NotNull ITransaction transaction);

  void onSpanStarted(@NotNull Span span);

  void onSpanFinished(@NotNull Span span);

  @Nullable
  List<PerformanceCollectionData> stop(@NotNull ITransaction transaction);

  /** Cancel the collector and stops it. Used on SDK close. */
  @ApiStatus.Internal
  void close();
}
