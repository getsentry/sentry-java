package io.sentry.compose.navigation3.lifecycle

import io.sentry.ITransaction
import io.sentry.SpanStatus
import io.sentry.protocol.App

/**
 * An [INavTransaction] handle backed by a started [ITransaction].
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Apply the thread safety policy from [NavScopes].
 */
internal class NavTransaction
private constructor(
  /** The backing transaction. */
  val transaction: ITransaction
) : INavTransaction {

  val name: String
    get() = transaction.name

  override fun setData(key: String, value: Any) {
    transaction.setData(key, value)
  }

  override fun setContext(key: String, value: Any) {
    transaction.setContext(key, value)
  }

  override fun setApp(app: App) {
    transaction.contexts.setApp(app)
  }

  /**
   * Finishes the backing transaction with its own status, or [OK][SpanStatus.OK] if it has none,
   * and then passes it to [onFinished].
   */
  fun finish(onFinished: (ITransaction) -> Unit) {
    if (!transaction.isFinished) {
      transaction.finish(transaction.status ?: SpanStatus.OK)
    }
    onFinished(transaction)
  }

  companion object {
    /** A handle backed by the already-started [transaction]. */
    fun started(transaction: ITransaction): NavTransaction = NavTransaction(transaction)
  }
}

/**
 * The subset of the [ITransaction] interface used to record nav data on a nav transaction.
 *
 * Protects against accidental misuse of what would otherwise be a partially-implemented
 * `ITransaction` interface. New operations against the underlying `ITransaction` instance must be
 * added here. Operations not directly delegable to `ITransaction` should ***not*** be added.
 *
 * Starting and finishing a nav transaction are lease-aware, so they go through [INavScope] instead.
 */
internal sealed interface INavTransaction {

  /** See [ITransaction.setData]. */
  fun setData(key: String, value: Any)

  /** See [ITransaction.setContext]. */
  fun setContext(key: String, value: Any)

  /** Sets the transaction's app context. See [ITransaction.getContexts]. */
  fun setApp(app: App)
}
