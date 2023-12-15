package io.sentry;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TransactionPerformanceCollector {

  void start(@NotNull ITransaction transaction);

  /**
   * Called whenever a new span (including the top level transaction) is started.
   *
   * @param span the span that was started
   */
  void onSpanStarted(@NotNull ISpan span);

  /**
   * Called whenever a span (including the top level transaction) is finished.
   *
   * @param span the span that was finished
   */
  void onSpanFinished(@NotNull ISpan span);

  @Nullable
  List<PerformanceCollectionData> stop(@NotNull ITransaction transaction);

  /** Cancel the collector and stops it. Used on SDK close. */
  @ApiStatus.Internal
  void close();
}
