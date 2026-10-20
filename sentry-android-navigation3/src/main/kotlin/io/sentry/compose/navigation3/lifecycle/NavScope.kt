package io.sentry.compose.navigation3.lifecycle

import io.sentry.IScope
import io.sentry.ITransaction
import io.sentry.PropagationContext
import io.sentry.protocol.App
import io.sentry.protocol.Contexts

/**
 * An [INavScope] implementation that writes to and reads from [delegate] only when [owner] holds
 * the nav lease; otherwise it falls back to [stagedNavData].
 */
internal class NavScope(
  private val delegate: IScope,
  private val stagedNavData: StagedNavData,
  private val coordinator: NavLeaseCoordinator,
  private val owner: Any,
) : INavScope {

  private val hasLease: Boolean
    get() = coordinator.hasLease(owner)

  override var screen: String?
    get() = if (hasLease) delegate.screen else stagedNavData.screen
    set(value) {
      stagedNavData.stageScreen(value)
      if (hasLease) {
        stagedNavData.publishScreen(delegate)
      }
    }

  override fun setNavigationContext(value: Any?) {
    stagedNavData.stageNavigationContext(value)
    if (hasLease) {
      stagedNavData.publishNavigationContext(delegate)
    }
  }

  override val app: App?
    get() = Contexts(delegate.contexts).app

  /**
   * The transaction bound to the scope. Without the lease, another owner's nav transaction isn't
   * reported, so [owner]'s attempt to start a nav transaction reaches [NavScopes.startTransaction],
   * which declines it and defers the propagation context rotation it implies. Non-nav transactions
   * (e.g., the host's) are always reported, so they still block nav transactions as usual.
   */
  override val transaction: ITransaction?
    get() =
      delegate.transaction?.takeUnless { current ->
        !hasLease && coordinator.isNavTransaction(current)
      }

  override fun bindTransaction(transaction: ITransaction) {
    if (!hasLease) {
      return
    }
    delegate.withTransaction { current ->
      if (current == null) {
        delegate.transaction = transaction
      }
    }
  }

  /**
   * Unbinds [transaction] if it's bound, and, with or without the lease, tells the [coordinator] to
   * stop retaining it, so a retired transaction isn't restored when [owner] regains the lease.
   */
  override fun unbindTransaction(transaction: ITransaction) {
    delegate.withTransaction { current ->
      if (current === transaction) {
        delegate.clearTransaction()
      }
    }
    coordinator.releaseTransaction(owner, transaction)
  }

  override fun clearFinishedTransaction() {
    if (!hasLease) {
      return
    }
    delegate.withTransaction { current ->
      if (current?.isFinished == true) {
        delegate.clearTransaction()
      }
    }
  }

  override fun rotatePropagationContext() {
    if (hasLease) {
      delegate.withPropagationContext { delegate.propagationContext = PropagationContext() }
    } else {
      stagedNavData.deferPropagationContextRotation()
    }
  }
}

/**
 * The subset of the [IScope] interface used to record navigation data.
 *
 * Protects against accidental misuse of what would otherwise be a partially-implemented `IScope`
 * interface. New operations against the underlying `IScope` instance must be added here. Operations
 * not directly delegable to `IScope` should ***not*** be added.
 */
internal interface INavScope {

  var screen: String?

  /** A copy of the scope's app context, if any. */
  val app: App?

  /**
   * The transaction bound to the scope, if any, as far as starting a nav transaction is concerned:
   * see [NavScope.transaction].
   */
  val transaction: ITransaction?

  /** Binds [transaction] to the scope if no transaction is bound to it. */
  fun bindTransaction(transaction: ITransaction)

  /**
   * Unbinds [transaction] from the scope if it's the bound transaction, and ensures it won't be
   * bound to the scope again.
   */
  fun unbindTransaction(transaction: ITransaction)

  /** Unbinds the scope's transaction if it has finished. */
  fun clearFinishedTransaction()

  /**
   * Sets or, given `null`, clears the value stored under [NAVIGATION_CONTEXT_KEY].
   *
   * Implementations should clear via [IScope.setContexts] rather than [IScope.removeContexts], as
   * the latter doesn't notify scope observers and therefore doesn't persist the removal to disk.
   */
  fun setNavigationContext(value: Any?)

  /** Replaces the scope's propagation context with a new one. */
  fun rotatePropagationContext()
}
