package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Used for performing operations when a transaction is started or ended. */
@ApiStatus.Internal
public interface ITransactionProfiler {
  void onTransactionStart(@NotNull ITransaction transaction);

  @Nullable
  ProfilingTraceData onTransactionFinish(@NotNull ITransaction transaction);
}
