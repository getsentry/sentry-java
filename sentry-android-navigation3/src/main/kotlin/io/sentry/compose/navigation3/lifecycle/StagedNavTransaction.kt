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
 * An `IScope` has a single, mutually-exclusive transaction slot. A transaction requested while
 * publishing isn't permitted is [staged][stage] rather than started, and starts when it's
 * [published][publishTo]. A started transaction that's [unpublished][unpublishFrom] is unbound but
 * keeps running, so that it can be bound again if it's republished while unfinished. Once
 * [finished][finish], a transaction is forgotten and never bound again.
 *
 * Starting or publishing a nav transaction never displaces an unfinished transaction bound to the
 * scope by someone else (e.g., the host app).
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Adhere to the thread safety policy from [NavScopes].
 */
internal class StagedNavTransaction(private val scopes: IScopes) {

  /**
   * The nav transaction most recently started or staged, unless it has been finished since then.
   */
  private var navTransaction: NavTransaction? = null

  /**
   * Returns `true` if [transaction] is this instance's started nav transaction, whether it's bound
   * to a scope or not.
   */
  fun owns(transaction: ITransaction): Boolean = navTransaction?.transaction === transaction

  /**
   * Starts a nav transaction from [context] and [options] and binds it to [scope], unless another
   * unfinished transaction is already bound to `scope`. Returns `null` if no transaction was
   * started (e.g., because it isn't sampled), in which case the instance's current nav transaction,
   * if any, is kept.
   *
   * **Warning**: [Finish][finish] the current nav transaction before calling this method, as this
   * method replaces the current transaction without finishing it.
   */
  fun start(
    scope: IScope,
    context: TransactionContext,
    options: TransactionOptions,
  ): NavTransaction? {
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
   * Stages a nav transaction that's started from [context] and [options] when it's
   * [published][publishTo], and returns its handle, which records what's set on it in the meantime.
   *
   * **Warning**: [Finish][finish] the current nav transaction before calling this method, as this
   * method replaces the current transaction without finishing it.
   */
  fun stage(context: TransactionContext, options: TransactionOptions): NavTransaction =
    NavTransaction.deferred(context, options).also { navTransaction = it }

  /**
   * Binds the nav transaction to [scope], starting it first if it's staged. A started transaction
   * that has finished in the meantime is forgotten and unbound instead.
   *
   * Does neither if another unfinished transaction is bound to [scope]; a staged transaction then
   * stays staged, so it can start the next time it's published. If the transaction fails to start
   * (e.g., because it isn't sampled), it also remains staged.
   */
  fun publishTo(scope: IScope) {
    val navTransaction = navTransaction ?: return
    val started = navTransaction.transaction
    if (started?.isFinished == true) {
      this.navTransaction = null
      unbind(scope, started)
    } else if (scope.isFreeFor(navTransaction)) {
      (started ?: navTransaction.start(scopes))?.let { bind(scope, it) }
    }
  }

  /**
   * Unbinds the nav transaction from [scope], if it's bound, without finishing it, so it can be
   * [published][publishTo] again.
   */
  fun unpublishFrom(scope: IScope) {
    navTransaction?.transaction?.let { unbind(scope, it) }
  }

  /**
   * Finishes the nav transaction and unbinds it from [scope], if it's bound. A staged transaction
   * is discarded instead.
   */
  fun finish(scope: IScope) {
    val navTransaction = navTransaction ?: return
    // Forget the transaction before unbinding it: unbinding notifies the host app's scope
    // observers, which can throw, and a transaction left behind would be bound again later.
    this.navTransaction = null
    navTransaction.finish(onFinished = { unbind(scope, it) })
  }

  /**
   * Forgets the nav transaction without finishing it, e.g., when there's no scope to unbind it
   * from. If started, it will be left to time out.
   */
  fun forget() {
    navTransaction = null
  }

  /**
   * Reports whether [navTransaction] can be bound to the receiver.
   *
   * Logs [navTransaction]'s route name if another unfinished transaction is already bound.
   */
  private fun IScope.isFreeFor(navTransaction: NavTransaction): Boolean {
    val current = transaction
    if (current == null || current.isFinished || current === navTransaction.transaction) {
      return true
    }

    scopes.options.logger.log(
      SentryLevel.DEBUG,
      "Nav3 transaction for route %s won't be bound because another transaction is active.",
      navTransaction.name,
    )
    return false
  }

  /**
   * Binds [transaction] to [scope] if no unfinished transaction is bound to it. A finished one is
   * about to be dropped from the scope anyway, so it's replaced.
   */
  private fun bind(scope: IScope, transaction: ITransaction) {
    scope.withTransaction { current ->
      if (current == null || current.isFinished) {
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
}
