package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface TransactionFinishedCallback {

  /**
   * Called when observed transaction finishes
   *
   * @param transaction the transaction
   */
  void execute(@NotNull ITransaction transaction);
}
