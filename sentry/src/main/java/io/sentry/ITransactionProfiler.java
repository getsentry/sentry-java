package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Used for performing operations when a transaction is started or ended. */
@ApiStatus.Internal
public interface ITransactionProfiler {
  void onTransactionStart(@NotNull ITransaction transaction);

  void onTransactionFinish(@NotNull ITransaction transaction);
}
