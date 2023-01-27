package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpTransactionProfiler implements ITransactionProfiler {

  private static final NoOpTransactionProfiler instance = new NoOpTransactionProfiler();

  private NoOpTransactionProfiler() {}

  public static NoOpTransactionProfiler getInstance() {
    return instance;
  }

  @Override
  public void onTransactionStart(@NotNull ITransaction transaction) {}

  @Override
  public @Nullable ProfilingTraceData onTransactionFinish(
      @NotNull ITransaction transaction, @Nullable PerformanceCollectionData memoryCollectionData) {
    return null;
  }
}
