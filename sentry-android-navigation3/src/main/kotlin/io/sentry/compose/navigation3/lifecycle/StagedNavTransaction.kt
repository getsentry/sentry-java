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
 * A transaction requested while publishing isn't permitted is [staged][stage] rather than started,
 * and starts when it's next [published][publishTo]. That publication is its only chance to start:
 * if it can't start then, it's discarded. A started transaction that's [unpublished][unpublishFrom]
 * is unbound but keeps running, so that it can be bound again if it's republished while unfinished.
 * Once [finished][finish], a transaction is forgotten and never bound again.
 *
 * Starting or publishing a nav transaction never displaces an unfinished transaction bound to the
 * scope by someone else (e.g., the host app).
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Apply the thread safety policy from [NavScopes].
 */
internal class StagedNavTransaction(private val scopes: IScopes) {

  /**
   * The nav transaction most recently started or staged, unless it has been finished since then.
   */
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
   * If another unfinished transaction is bound to [scope], a started transaction stays unbound, so
   * it can be bound the next time it's published. A staged transaction is discarded instead, as it
   * is if [scopes] return a no-op transaction for it (see [start]).
   */
  fun publishTo(scope: IScope) {
    val navTransaction = navTransaction ?: return
    val started = navTransaction.transaction
    when {
      started?.isFinished == true -> {
        this.navTransaction = null
        unbind(scope, started)
      }
      started != null -> if (scope.isFreeFor(navTransaction.name, started)) bind(scope, started)
      else -> {
        val startedNow =
          if (scope.isFreeFor(navTransaction.name, transaction = null)) {
            navTransaction.start(scopes)
          } else {
            null
          }
        if (startedNow != null) {
          bind(scope, startedNow)
        } else {
          this.navTransaction = null
          navTransaction.finish(onFinished = {})
        }
      }
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
