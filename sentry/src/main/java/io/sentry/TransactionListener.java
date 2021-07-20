package io.sentry;

import org.jetbrains.annotations.NotNull;

public interface TransactionListener {

  /**
   * Called when observed transaction finishes
   *
   * @param transaction the transaction
   */
  void onTransactionFinished(@NotNull ITransaction transaction);
}
