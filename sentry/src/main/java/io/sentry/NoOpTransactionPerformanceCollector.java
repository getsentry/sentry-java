package io.sentry;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpTransactionPerformanceCollector implements TransactionPerformanceCollector {

  private static final NoOpTransactionPerformanceCollector instance =
      new NoOpTransactionPerformanceCollector();

  public static NoOpTransactionPerformanceCollector getInstance() {
    return instance;
  }

  private NoOpTransactionPerformanceCollector() {}

  @Override
  public void start(@NotNull ITransaction transaction) {}

  @Override
  public void onSpanStarted(@NotNull ISpan span) {}

  @Override
  public void onSpanFinished(@NotNull ISpan span) {}

  @Override
  public @Nullable List<PerformanceCollectionData> stop(@NotNull ITransaction transaction) {
    return null;
  }

  @Override
  public void close() {}
}
