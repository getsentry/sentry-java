package io.sentry.profiling;

import io.sentry.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class JvmTransactionProfiler implements ITransactionProfiler {
  @Override
  public void onTransactionStart(@NotNull ITransaction transaction) {

  }

  @Override
  public @Nullable ProfilingTraceData onTransactionFinish(@NotNull ITransaction transaction, @Nullable List<PerformanceCollectionData> performanceCollectionData) {
    return null;
  }

  @Override
  public void close() {

  }
}
