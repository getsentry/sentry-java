package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.PropagationContext
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.ScopeType
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions

private const val NAVIGATION_CONTEXT_KEY: String = "navigation"

/**
 * Gives one [BackStackObserver] lifecycle-aware access to navigation state shared through
 * [delegate].
 *
 * Navigation data is published while the lifecycle is resumed. Navigation transactions remain
 * active from attachment until destruction so the initial destination can create its transaction
 * during `Activity.onCreate`.
 *
 * This class is not thread-safe. Lifecycle and back stack callbacks are expected on the same
 * thread.
 */
internal class NavIScopes(
  private val delegate: IScopes,
  private val coordinator: NavLeaseCoordinator = sharedNavLeaseCoordinator,
) : IScopes by delegate, LifecycleEventObserver {

  private val owner: Any = Any()

  // Empty options prevent shadow writes from notifying the real SDK's scope observers.
  private val shadowScope: Scope = Scope(SentryOptions.empty())

  private var hasScreenUpdate: Boolean = false
  private var hasNavigationContextUpdate: Boolean = false
  private var hasPublishedScreen: Boolean = false
  private var publishedScreen: String? = null
  private var hasPublishedNavigationContext: Boolean = false
  private var hasPublishedBreadcrumb: Boolean = false
  private var pendingInitialBreadcrumb: PendingBreadcrumb? = null
  private var isProcessingBackStackUpdate: Boolean = false

  /** Initializes lease state without changing Sentry scope or transaction state. */
  fun attach(lifecycle: Lifecycle) {
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
      return
    }

    coordinator.claimTransactionOwnership(owner)
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      claimDataOwnership()
    }
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    when (event) {
      Lifecycle.Event.ON_CREATE -> coordinator.claimTransactionOwnership(owner)
      Lifecycle.Event.ON_RESUME -> claimDataOwnership()
      Lifecycle.Event.ON_PAUSE -> releaseDataOwnership()
      Lifecycle.Event.ON_DESTROY -> dispose()
      else -> Unit
    }
  }

  /** Releases this observer's leases and any transaction it still owns. */
  fun dispose() {
    releaseDataOwnership()
    delegate.configureScope { scope -> coordinator.releaseTransactionOwnership(owner, scope) }
  }

  /**
   * Runs [callback] as a back stack update eligible to replace the previous owner's transaction.
   */
  fun runBackStackUpdate(callback: () -> Unit) {
    isProcessingBackStackUpdate = true
    try {
      callback()
    } finally {
      isProcessingBackStackUpdate = false
    }
  }

  override fun configureScope(scopeType: ScopeType?, callback: ScopeCallback) {
    val leasedCallback = ScopeCallback { scope ->
      if (isProcessingBackStackUpdate) {
        coordinator.prepareTransactionUpdate(owner, scope)
      }
      callback.run(LeasedNavigationScope(scope))
      coordinator.clearFinishedTransaction()
    }
    if (scopeType == null) {
      delegate.configureScope(leasedCallback)
    } else {
      delegate.configureScope(scopeType, leasedCallback)
    }
  }

  override fun addBreadcrumb(breadcrumb: Breadcrumb, hint: Hint?) {
    if (coordinator.ownsData(owner)) {
      delegate.addBreadcrumb(breadcrumb, hint)
      hasPublishedBreadcrumb = true
      pendingInitialBreadcrumb = null
    } else if (!hasPublishedBreadcrumb) {
      pendingInitialBreadcrumb = PendingBreadcrumb(breadcrumb, hint)
    }
  }

  override fun addBreadcrumb(breadcrumb: Breadcrumb) {
    if (coordinator.ownsData(owner)) {
      delegate.addBreadcrumb(breadcrumb)
      hasPublishedBreadcrumb = true
      pendingInitialBreadcrumb = null
    } else if (!hasPublishedBreadcrumb) {
      pendingInitialBreadcrumb = PendingBreadcrumb(breadcrumb, null)
    }
  }

  override fun startTransaction(
    transactionContext: TransactionContext,
    transactionOptions: TransactionOptions,
  ): ITransaction {
    if (!coordinator.ownsTransactions(owner)) {
      return NoOpTransaction.getInstance()
    }

    return delegate.startTransaction(transactionContext, transactionOptions).also { transaction ->
      if (!transaction.isNoOp) {
        coordinator.bindTransaction(owner, transaction)
      }
    }
  }

  private fun claimDataOwnership() {
    if (!coordinator.claimDataOwnership(owner)) {
      return
    }

    delegate.configureScope { scope ->
      if (!coordinator.ownsData(owner)) {
        return@configureScope
      }
      if (hasScreenUpdate) {
        scope.screen = shadowScope.screen
        hasPublishedScreen = true
        publishedScreen = shadowScope.screen
      }
      if (hasNavigationContextUpdate) {
        scope.setContexts(NAVIGATION_CONTEXT_KEY, shadowScope.contexts[NAVIGATION_CONTEXT_KEY])
        hasPublishedNavigationContext = true
      }
    }

    pendingInitialBreadcrumb?.let { pending ->
      if (coordinator.ownsData(owner)) {
        delegate.addBreadcrumb(pending.breadcrumb, pending.hint)
        hasPublishedBreadcrumb = true
        pendingInitialBreadcrumb = null
      }
    }
  }

  private fun releaseDataOwnership() {
    if (!coordinator.releaseDataOwnership(owner)) {
      return
    }

    delegate.configureScope { scope ->
      // For now, clear data on ON_PAUSE so unowned global scope state cannot linger.
      // TODO ADAM: Re-evaluate whether paused observers should preserve their last published data.
      if (hasPublishedScreen && scope.screen == publishedScreen) {
        scope.screen = null
      }
      if (hasPublishedNavigationContext) {
        scope.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
      }
    }
    hasPublishedScreen = false
    publishedScreen = null
    hasPublishedNavigationContext = false
  }

  private inner class LeasedNavigationScope(private val realScope: IScope) : IScope by realScope {

    override fun getScreen(): String? =
      if (coordinator.ownsData(owner)) realScope.screen else shadowScope.screen

    override fun setScreen(screen: String?) {
      shadowScope.screen = screen
      hasScreenUpdate = true
      if (coordinator.ownsData(owner)) {
        realScope.screen = screen
        hasPublishedScreen = true
        publishedScreen = screen
      }
    }

    override fun setContexts(key: String?, value: Any?) {
      if (key != NAVIGATION_CONTEXT_KEY) {
        realScope.setContexts(key, value)
        return
      }

      shadowScope.setContexts(key, value)
      hasNavigationContextUpdate = true
      if (coordinator.ownsData(owner)) {
        realScope.setContexts(key, value)
        hasPublishedNavigationContext = true
      }
    }

    override fun getPropagationContext(): PropagationContext =
      if (coordinator.ownsTransactions(owner)) {
        realScope.propagationContext
      } else {
        shadowScope.propagationContext
      }

    override fun setPropagationContext(propagationContext: PropagationContext) {
      if (coordinator.ownsTransactions(owner)) {
        realScope.propagationContext = propagationContext
      } else {
        shadowScope.propagationContext = propagationContext
      }
    }

    override fun withPropagationContext(
      callback: Scope.IWithPropagationContext
    ): PropagationContext =
      if (coordinator.ownsTransactions(owner)) {
        realScope.withPropagationContext(callback)
      } else {
        shadowScope.withPropagationContext(callback)
      }
  }

  private data class PendingBreadcrumb(val breadcrumb: Breadcrumb, val hint: Hint?)
}

/** Coordinates leases for navigation state shared by multiple [SentryNavEffect] instances. */
internal class NavLeaseCoordinator {

  private var dataOwner: Any? = null
  private var transactionOwner: Any? = null
  private var activeTransactionOwner: Any? = null
  private var activeTransaction: ITransaction? = null

  fun claimDataOwnership(owner: Any): Boolean {
    if (dataOwner === owner) {
      return false
    }
    dataOwner = owner
    return true
  }

  fun ownsData(owner: Any): Boolean = dataOwner === owner

  fun releaseDataOwnership(owner: Any): Boolean {
    if (!ownsData(owner)) {
      return false
    }
    dataOwner = null
    return true
  }

  fun claimTransactionOwnership(owner: Any): Boolean {
    if (transactionOwner === owner) {
      return false
    }
    transactionOwner = owner
    return true
  }

  fun ownsTransactions(owner: Any): Boolean = transactionOwner === owner

  /** Stops the previous owner's transaction when [owner] next processes a back stack update. */
  fun prepareTransactionUpdate(owner: Any, scope: IScope) {
    clearFinishedTransaction()
    if (ownsTransactions(owner) && activeTransactionOwner !== owner) {
      stopActiveTransaction(scope)
    }
  }

  fun bindTransaction(owner: Any, transaction: ITransaction) {
    if (ownsTransactions(owner)) {
      activeTransactionOwner = owner
      activeTransaction = transaction
    }
  }

  fun releaseTransactionOwnership(owner: Any, scope: IScope) {
    if (activeTransactionOwner === owner) {
      stopActiveTransaction(scope)
    }
    if (ownsTransactions(owner)) {
      transactionOwner = activeTransactionOwner
    }
  }

  fun clearFinishedTransaction() {
    if (activeTransaction?.isFinished == true) {
      activeTransactionOwner = null
      activeTransaction = null
    }
  }

  private fun stopActiveTransaction(scope: IScope) {
    val transaction = activeTransaction ?: return
    if (!transaction.isFinished) {
      transaction.finish(transaction.status ?: SpanStatus.OK)
    }
    scope.withTransaction { currentTransaction ->
      if (currentTransaction === transaction) {
        scope.clearTransaction()
      }
    }
    activeTransactionOwner = null
    activeTransaction = null
  }
}

internal val sharedNavLeaseCoordinator: NavLeaseCoordinator = NavLeaseCoordinator()
