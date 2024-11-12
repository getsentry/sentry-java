package io.sentry;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CompositePerformanceCollector {

  /** Starts collecting performance data and span related data (e.g. slow/frozen frames). */
  void start(@NotNull ITransaction transaction);

  /** Starts collecting performance data without span related data (e.g. slow/frozen frames). */
  void start(@NotNull String id);

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

  /** Stops collecting performance data and span related data (e.g. slow/frozen frames). */
  @Nullable
  List<PerformanceCollectionData> stop(@NotNull ITransaction transaction);

  /** Stops collecting performance data. */
  @Nullable
  List<PerformanceCollectionData> stop(@NotNull String id);

  /** Cancel the collector and stops it. Used on SDK close. */
  @ApiStatus.Internal
  void close();
}
