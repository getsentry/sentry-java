package io.sentry;

import org.jetbrains.annotations.ApiStatus;

/** Used for performing operations when a transaction is started or ended. */
@ApiStatus.Internal
public interface ITransactionListener {
  void onTransactionStart(ITransaction transaction);

  void onTransactionEnd(ITransaction transaction);
}
