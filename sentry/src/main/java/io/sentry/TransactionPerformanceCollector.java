package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TransactionPerformanceCollector {

  void start(@NotNull ITransaction transaction);

  @Nullable
  PerformanceCollectionData stop(@NotNull ITransaction transaction);
}
