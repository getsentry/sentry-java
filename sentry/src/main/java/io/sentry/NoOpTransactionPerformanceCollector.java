package io.sentry;

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
  public @Nullable PerformanceCollectionData stop(@NotNull ITransaction transaction) {
    return null;
  }
}
