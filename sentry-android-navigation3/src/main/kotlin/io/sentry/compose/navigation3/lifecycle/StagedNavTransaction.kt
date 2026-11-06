package io.sentry.compose.navigation3.lifecycle

import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SentryLevel
import io.sentry.SpanStatus

/**
 * Holds a nav transaction staged for publication to a shared [IScope].
 *
 * A [staged][stage] request starts a transaction when it's next [published][publishTo], which
 * happens right away if the caller holds the nav lease. An [unpublished][unpublishFrom] transaction
 * keeps running and can be rebound. [finish] forgets both the transaction and its request.
 *
 * If a transaction ends on its own, cannot start, or is discarded, its request remains available to
 * start a new transaction when the nav root returns.
 *
 * **Sampling**
 *
 * Sampled and unsampled transactions behave alike while running. Returning to a nav root reuses its
 * unfinished transaction and sampling decision; starting a new transaction on return makes a new
 * sampling decision.
 *
 * **Precedence**
 *
 * Navigation doesn't displace an unfinished transaction already bound to the shared `IScope`. This
 * includes `ui.load`, `ui.action`, and host-app transactions. Changing their relative priority
 * requires an SDK-wide policy.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Apply the thread safety policy from [NavScopes].
 */
internal class StagedNavTransaction(private val scopes: IScopes) {

  /**
   * The request most recently passed to [stage], whatever became of its transaction. Cleared when
   * the nav transaction is [finished][finish] or [forgotten][forget], i.e., when the destination it
   * describes is left.
   *
   * While there's a request but no [transaction], a transaction is started from the request when
   * it's next [published][publishTo]: it's staged, or was discarded or blocked from starting.
   */
  private var request: NavTransactionRequest? = null

  /** The transaction most recently started for [request], unless it has been forgotten since. */
  private var transaction: ITransaction? = null

  /**
   * Stages [request], so that the next [publishTo] starts a transaction for it.
   *
   * Call [finish] first: staging replaces the current transaction without finishing it.
   */
  fun stage(request: NavTransactionRequest) {
    this.request = request
    transaction = null
  }

  /**
   * Updates the saved request for a future start without changing a running transaction.
   *
   * No-ops if no request has been [staged][stage].
   */
  fun updateRequest(request: NavTransactionRequest) {
    if (this.request == null) {
      return
    }
    this.request = request
  }

  /**
   * Publishes the staged transaction by binding it to [scope], or starts a transaction from the
   * staged request if needed. An occupied scope slot leaves the transaction unbound and the request
   * available for later publication.
   */
  fun publishTo(scope: IScope) {
    val request = request ?: return
    val started = transaction

    if (started != null && !started.isFinished) {
      if (scope.isFreeFor(request.name, started)) {
        bind(scope, started)
      }
      return
    }

    transaction = null
    started?.let { unbind(scope, it) }
    startAndBind(scope, request)
  }

  /** Unbinds the transaction from [scope] without finishing it, allowing later publication. */
  fun unpublishFrom(scope: IScope) {
    transaction?.let { unbind(scope, it) }
  }

  /**
   * Finishes and unbinds the transaction from [scope], or discards it if staged. Uses its current
   * status, or [OK][SpanStatus.OK] if unset. Forgets the request in either case.
   */
  fun finish(scope: IScope) {
    request = null
    val transaction = transaction ?: return
    // Forget the transaction before unbinding it: unbinding notifies the host app's scope
    // observers, which can throw, and a transaction left behind would be bound again later.
    this.transaction = null
    if (!transaction.isFinished) {
      transaction.finish(transaction.status ?: SpanStatus.OK)
    }
    unbind(scope, transaction)
  }

  /** Forgets the transaction and request without finishing or unbinding a started transaction. */
  fun forget() {
    request = null
    transaction = null
  }

  /** Whether the slot is empty, holds a finished transaction, or already holds [transaction]. */
  private fun canHold(current: ITransaction?, transaction: ITransaction?): Boolean =
    current == null || current.isFinished || current === transaction

  /**
   * Starts and binds a transaction based on the provided [request] if [scope]'s slot is free.
   *
   * Returns `null` if the slot is occupied or [scopes] declines the start.
   */
  private fun startAndBind(scope: IScope, request: NavTransactionRequest): ITransaction? {
    if (!scope.isFreeFor(request.name, transaction = null)) {
      return null
    }
    val transaction = request.start(scopes, publishingScope = scope) ?: return null

    // Record the transaction before binding it: binding notifies the host app's scope observers,
    // which can throw, and an unrecorded transaction might never be finished.
    this.transaction = transaction

    bind(scope, transaction)
    return transaction
  }

  /** Binds [transaction] if [scope]'s slot is empty, finished, or already holds it. */
  private fun bind(scope: IScope, transaction: ITransaction) {
    scope.withTransaction { current ->
      if (current !== transaction && canHold(current, transaction)) {
        scope.transaction = transaction
      }
    }
  }

  /** Unbinds [transaction] from [scope] if it's the bound transaction. */
  private fun unbind(scope: IScope, transaction: ITransaction) {
    scope.withTransaction { current ->
      if (current === transaction) {
        scope.clearTransaction()
      }
    }
  }

  /**
   * Checks whether this scope can hold [transaction] (or a new one if `null`), logging a blocker.
   */
  private fun IScope.isFreeFor(name: String, transaction: ITransaction?): Boolean {
    if (canHold(current = this.transaction, transaction)) {
      return true
    }

    scopes.options.logger.log(
      SentryLevel.DEBUG,
      "Nav3 transaction for route %s is blocked by another active transaction.",
      name,
    )
    return false
  }
}
