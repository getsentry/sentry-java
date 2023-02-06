package io.sentry;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TransactionPerformanceCollector {

  void start(@NotNull ITransaction transaction);

  @Nullable
  List<PerformanceCollectionData> stop(@NotNull ITransaction transaction);
}
