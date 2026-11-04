package io.sentry.compose.navigation3.lifecycle

import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.SentryLevel
import io.sentry.TransactionContext
import io.sentry.TransactionOptions

/**
 * Holds a nav transaction staged for publication to a shared [IScope].
 *
 * A started transaction that's [unpublished][unpublishFrom] is unbound but keeps running, so that
 * it can be bound again if it's republished while unfinished. Once [finished][finish], a
 * transaction is forgotten and never bound again.
 *
 * Starting or publishing a nav transaction never displaces an unfinished transaction bound to the
 * scope by someone else (e.g., the host app).
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Apply the thread safety policy from [NavScopes].
 */
internal class StagedNavTransaction(private val scopes: IScopes) {

  /** The nav transaction most recently started, unless it has been finished since then. */
  private var navTransaction: NavTransaction? = null

  /**
   * Starts a nav transaction from [context] and [options] and binds it to [scope], unless another
   * unfinished transaction is already bound to `scope`, in which case none is started, as it
   * couldn't be bound. Returns `null` if no transaction was started (including when [scopes]
   * returned a no-op transaction, e.g., because the SDK is disabled or ignores the transaction's
   * origin), in which case the instance's current nav transaction, if any, is kept.
   *
   * **Warning**: [Finish][finish] the current nav transaction before calling this method, as this
   * method replaces the current transaction without finishing it.
   */
  fun start(
    scope: IScope,
    context: TransactionContext,
    options: TransactionOptions,
  ): NavTransaction? {
    if (!scope.isFreeFor(context.name, transaction = null)) {
      return null
    }
    val transaction =
      scopes.startTransaction(context, options).takeUnless { it.isNoOp } ?: return null

    // Record the transaction before binding it: binding notifies the host app's scope observers,
    // which can throw, and an unrecorded transaction might never be finished.
    val started = NavTransaction.started(transaction)
    navTransaction = started

    bind(scope, transaction)
    return started
  }

  /**
   * Binds the nav transaction to [scope]. A transaction that has finished in the meantime is
   * forgotten and unbound instead.
   *
   * If another unfinished transaction is bound to [scope], the transaction stays unbound, so it can
   * be bound the next time it's published.
   */
  fun publishTo(scope: IScope) {
    val navTransaction = navTransaction ?: return
    val started = navTransaction.transaction
    if (started.isFinished) {
      this.navTransaction = null
      unbind(scope, started)
    } else if (scope.isFreeFor(navTransaction.name, started)) {
      bind(scope, started)
    }
  }

  /**
   * Unbinds the nav transaction from [scope], if it's bound, without finishing it, so it can be
   * [published][publishTo] again.
   */
  fun unpublishFrom(scope: IScope) {
    navTransaction?.let { unbind(scope, it.transaction) }
  }

  /** Finishes the nav transaction and unbinds it from [scope], if it's bound. */
  fun finish(scope: IScope) {
    val navTransaction = navTransaction ?: return
    // Forget the transaction before unbinding it: unbinding notifies the host app's scope
    // observers, which can throw, and a transaction left behind would be bound again later.
    this.navTransaction = null
    navTransaction.finish(onFinished = { unbind(scope, it) })
  }

  /**
   * Forgets the nav transaction without finishing it, e.g., when there's no scope to unbind it
   * from. It will be left to time out.
   */
  fun forget() {
    navTransaction = null
  }

  /**
   * Reports whether the receiver's transaction slot can hold [transaction], or a new transaction
   * given `null` (see [canHold]).
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

  /** Binds [transaction] to [scope] if the scope's transaction slot can hold it (see [canHold]). */
  private fun bind(scope: IScope, transaction: ITransaction) {
    scope.withTransaction { current ->
      if (current !== transaction && canHold(current, transaction)) {
        scope.transaction = transaction
      }
    }
  }

  /**
   * Whether a transaction slot holding [current] can hold [transaction], or a new transaction given
   * `null`: it's empty, holds a finished transaction, or already holds `transaction`.
   */
  private fun canHold(current: ITransaction?, transaction: ITransaction?): Boolean =
    current == null || current.isFinished || current === transaction

  /** Unbinds [transaction] from [scope] if it's the bound transaction. */
  private fun unbind(scope: IScope, transaction: ITransaction) {
    scope.withTransaction { current ->
      if (current === transaction) {
        scope.clearTransaction()
      }
    }
  }
}
