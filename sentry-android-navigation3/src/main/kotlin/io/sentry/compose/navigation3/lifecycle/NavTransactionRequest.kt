package io.sentry.compose.navigation3.lifecycle

import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App
import io.sentry.protocol.Contexts
import io.sentry.protocol.TransactionNameSource

/** The operation of nav transactions, and the type and category of nav breadcrumbs. */
internal const val NAVIGATION_OP: String = "navigation"

/**
 * Everything needed to start a nav transaction for a destination.
 *
 * Transactions started from a request are named [name] with [TransactionNameSource.ROUTE] as their
 * name source, and have the operation [NAVIGATION_OP].
 *
 * A request may be reused to start a new transaction if the user returns to a nav root after its
 * transaction finishes. Each [start] creates a new transaction for the same destination.
 */
internal class NavTransactionRequest(
  val name: String,
  /** Builds the options for each transaction started from this request. */
  private val newOptions: () -> TransactionOptions,
  /** Data set on each transaction started from this request. See [ITransaction.setData]. */
  private val data: Map<String, Any> = emptyMap(),
  /** Contexts set on each transaction started from this request. See [ITransaction.setContext]. */
  private val contexts: Map<String, Any> = emptyMap(),
  /**
   * The view names to set on the app context of each transaction started from this request, or
   * `null` to leave the transaction's app context alone. The rest of the app context is copied from
   * the scope when the transaction starts.
   */
  private val viewNames: List<String>? = null,
) {

  companion object {

    private const val TRANSACTION_ORIGIN = "auto.navigation.nav3"

    /** Creates a request whose transaction options are read when each transaction starts. */
    fun forDestination(
      name: String,
      arguments: Map<String, Any?>,
      contexts: Map<String, Any>,
      viewNames: List<String>?,
      options: SentryOptions,
    ): NavTransactionRequest =
      NavTransactionRequest(
        name = name,
        newOptions = {
          TransactionOptions().also {
            it.isWaitForChildren = true
            it.idleTimeout = options.idleTimeout
            val deadlineTimeoutMillis = options.deadlineTimeout
            it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis
            it.isTrimEnd = true
            it.origin = TRANSACTION_ORIGIN
          }
        },
        data = if (arguments.isNotEmpty()) mapOf("arguments" to arguments) else emptyMap(),
        contexts = contexts,
        viewNames = viewNames,
      )
  }

  /**
   * Starts a transaction via [scopes] with this request's data and contexts. Copies the app context
   * from [publishingScope] when setting view names, without binding the transaction to that scope.
   *
   * Returns `null` if [scopes] declined to start one (i.e., returned a no-op transaction, e.g.,
   * because the SDK is disabled or ignores the transaction's origin).
   */
  fun start(scopes: IScopes, publishingScope: IScope): ITransaction? {
    val transaction =
      scopes
        .startTransaction(
          TransactionContext(name, TransactionNameSource.ROUTE, NAVIGATION_OP),
          newOptions(),
        )
        .takeUnless { it.isNoOp } ?: return null

    data.forEach { (key, value) -> transaction.setData(key, value) }
    contexts.forEach { (key, value) -> transaction.setContext(key, value) }
    viewNames?.let {
      transaction.contexts.setApp(publishingScope.appCopy().apply { this.viewNames = it })
    }
    return transaction
  }

  /**
   * A copy of the receiver's app context, or a new one if it has none, so that setting view names
   * on it leaves the scope's own app context alone.
   *
   * [App]'s copy constructor isn't public, so this copies all of [IScope.getContexts] and discards
   * all of it but the `App`.
   */
  private fun IScope.appCopy(): App = Contexts(contexts).app ?: App()
}
