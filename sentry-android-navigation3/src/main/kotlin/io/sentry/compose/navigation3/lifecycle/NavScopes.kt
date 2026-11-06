package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.PropagationContext
import io.sentry.SentryLevel
import io.sentry.SentryOptions

/** The key under which nav context (i.e., the captured back stack) is stored in a scope. */
internal const val NAVIGATION_CONTEXT_KEY: String = "Navigation"

/**
 * An [INavScopes] implementation that lets us coordinate updates to navigation state across
 * multiple `SentryNavEffect`s. (Also supports the trivial scenario where only a single
 * `SentryNavEffect` exists.)
 *
 * Needed because all `SentryNavEffect`s mutate a single [IScope] instance by default and will
 * clobber or interleave one another's updates unless properly managed:
 * ```
 * Nav root (Activity or Fragment)
 *       ↓ owns / calls
 *  SentryNavEffect
 *       ↓
 *   CoordinatedBackStackObserver (wrapper)
 *     ├── BackStackObserver   — computes nav data and requests nav transactions
 *     └── NavScopes           — controls if nav data + txns reach the real scope
 *               ↓
 *        NavLeaseCoordinator — arbitrates ownership across all observers
 *               ↓
 *          shared IScope
 * ```
 *
 * **Only leaseholders start nav transactions or publish nav data**
 *
 * This class works by only allowing `NavScopes` instances holding a lease to start nav transactions
 * and publish their navigation data to the shared `IScope`. (The lease policy is implemented by
 * [NavLeaseCoordinator].)
 *
 * `NavScopes` instances without a lease write to their own copy of [stagedNavTransaction] and
 * [stagedNavData], which lets them preserve their latest updates without overwriting another
 * instance's transaction or published data. When an instance acquires the lease, it writes its
 * staged transaction and data to the shared `IScope` (subject to any publishing conditions, e.g.,
 * staged transactions are dropped if another transaction is already bound to the shared `IScope`).
 *
 * **Usage**
 *
 * 1. Create a `NavScopes` instance per `SentryNavEffect`.
 *
 * 2. Pass the same [NavLeaseCoordinator] instance to all `NavScopes` whose effects must be
 *    coordinated (i.e., that target the same `IScope`).
 *
 * 3. Call [attach] with the effect's [nav root][NavRootInfo] before the first back stack update.
 *
 * 4. Call [onBackStackUpdate] before applying each back stack update.
 *
 * 5. Call [dispose] when the effect leaves the composition.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Lifecycle callbacks and method invocations should be
 * confined to the same thread.
 */
internal class NavScopes(
  private val delegate: IScopes,
  private val coordinator: NavLeaseCoordinator,
) : INavScopes {

  override val options: SentryOptions
    get() = delegate.options

  private var attachedLifecycle: Lifecycle? = null

  /** This instance's registration with the [coordinator], while it's attached. */
  private var registration: NavLeaseRegistration? = null

  private val stagedNavTransaction = StagedNavTransaction(delegate)
  private val stagedNavData = StagedNavData()

  private val navStatePublisher = NavStatePublisher()

  /**
   * Registers this [NavScopes] instance with the lifecycle of the effect's nav root, described by
   * [rootInfo].
   *
   * No-ops if the caller tries to attach more than once or if the nav root has already been
   * destroyed.
   */
  fun attach(rootInfo: NavRootInfo) {
    val currentLifecycle = attachedLifecycle
    val incomingLifecycle = rootInfo.lifecycle

    if (currentLifecycle != null) {
      if (currentLifecycle !== incomingLifecycle) {
        delegate.options.logger.log(
          SentryLevel.WARNING,
          "NavScopes is already attached to a different lifecycle; ignoring the new lifecycle.",
        )
      }

      // No-op if already attached to a lifecycle instance (whether the same or a different one).
      return
    }

    if (incomingLifecycle.currentState == Lifecycle.State.DESTROYED) {
      return
    }

    attachedLifecycle = incomingLifecycle
    registration = coordinator.register(delegate, rootInfo, navStatePublisher)
  }

  /**
   * Announces that a back stack update is about to be applied, so this instance can acquire the nav
   * lease if it's eligible.
   *
   * Must be called before each update.
   */
  fun onBackStackUpdate() {
    registration?.onBackStackUpdate()
  }

  override fun configureScope(callback: (INavScope) -> Unit) {
    delegate.configureScope { scope -> callback(NavScope(scope)) }
  }

  override fun addBreadcrumb(breadcrumb: Breadcrumb, hint: Hint) {
    delegate.addBreadcrumb(breadcrumb, hint)
  }

  /**
   * Releases the lease (if held), unpublishes this instance's nav state, and finishes its nav
   * transaction.
   */
  fun dispose() {
    registration?.dispose()
    stagedNavTransaction.forget()
    attachedLifecycle = null
    registration = null
  }

  /**
   * Publishes this instance's [stagedNavTransaction] and [stagedNavData] to the shared [IScope]
   * while it holds the nav lease, as directed by the [coordinator].
   */
  private inner class NavStatePublisher : INavStatePublisher {

    override fun publishTo(scope: IScope) {
      // Publishing the data first ensures a staged transaction is sampled against the rotated
      // propagation context when it starts.
      stagedNavData.publishTo(scope)
      stagedNavTransaction.publishTo(scope)
    }

    override fun unpublishFrom(scope: IScope) {
      stagedNavTransaction.unpublishFrom(scope)
      stagedNavData.clearPublishedStateFrom(scope)
    }

    override fun onUnregistered(scope: IScope?) {
      // Unregistering on destroy detaches this instance, as dispose() does.
      attachedLifecycle = null
      registration = null

      if (scope != null) {
        stagedNavTransaction.finish(scope)
      } else {
        // Without a scope (e.g., the SDK is disabled), the transaction can't be sent and will time
        // out anyway. Not finishing it immediately lets us ensure the error-handling policy from
        // IScopes.configureScope covers all SDK work.
        stagedNavTransaction.forget()
      }
    }
  }

  /**
   * An [INavScope] that maintains a [stagedNavTransaction] and [stagedNavData], and only writes to
   * or reads from the [delegate IScope][IScope] when this instance holds the nav lease.
   */
  private inner class NavScope(private val delegate: IScope) : INavScope {

    override var screen: String?
      get() = if (hasLease) delegate.screen else stagedNavData.screen
      set(value) {
        stagedNavData.stageScreen(value)
        if (hasLease) {
          stagedNavData.publishScreen(delegate)
        }
      }

    private val hasLease: Boolean
      get() = registration?.hasLease == true

    override fun setNavigationContext(value: Any?) {
      stagedNavData.stageNavigationContext(value)
      if (hasLease) {
        stagedNavData.publishNavigationContext(delegate)
      }
    }

    override fun rotatePropagationContext() {
      if (hasLease) {
        delegate.withPropagationContext { delegate.propagationContext = PropagationContext() }
      } else {
        stagedNavData.deferPropagationContextRotation()
      }
    }

    override fun updateNavTransactionRequest(request: NavTransactionRequest) {
      stagedNavTransaction.updateRequest(request)
    }

    /**
     * Starts and binds a nav transaction with the nav lease, or defers it until the lease is
     * acquired (see [StagedNavTransaction]). Callers must [finish][finishNavTransaction] the
     * previous nav transaction first.
     *
     * **Deferred transactions may mis-parent spans generated before lease acquisition**
     *
     * A deferred transaction starts when the lease is acquired, which may be later than when its
     * destination starts doing work (e.g., ViewPager2 can precompose offscreen pages, or an
     * Activity that navigates from a result callback before it resumes). Spans created in the
     * meantime attach to whichever transaction is bound to the scope at that time, usually the
     * leaseholder's.
     */
    override fun startNavTransaction(request: NavTransactionRequest) {
      stagedNavTransaction.stage(request)
      if (hasLease) {
        stagedNavTransaction.publishTo(delegate)
      } else {
        stagedNavData.deferPropagationContextRotation()
      }
    }

    override fun finishNavTransaction() {
      stagedNavTransaction.finish(delegate)
    }
  }
}

/**
 * The subset of the [IScopes] interface used to record nav state.
 *
 * Protects against accidental misuse of what would otherwise be a partially-implemented `IScopes`
 * interface. New operations against the underlying `IScopes` instance must be added here.
 * Operations not directly delegable to `IScopes` should ***not*** be added.
 */
internal sealed interface INavScopes {

  val options: SentryOptions

  /**
   * Runs [callback] with the current [INavScope].
   *
   * No-ops if the SDK doesn't provide a scope (e.g., the SDK is disabled).
   */
  fun configureScope(callback: (INavScope) -> Unit)

  fun addBreadcrumb(breadcrumb: Breadcrumb, hint: Hint)
}

/**
 * The scope operations used to record nav data and nav transactions.
 *
 * Protects against accidental misuse of what would otherwise be a partially-implemented `IScope`
 * interface. New operations against the underlying `IScope` instance must be added here. Operations
 * not directly delegable to `IScope` should ***not*** be added. The exception is the nav
 * transaction lifecycle ([startNavTransaction] and [finishNavTransaction]), which depends on the
 * nav lease and so has no `IScope` equivalent.
 */
internal sealed interface INavScope {

  var screen: String?

  /**
   * Sets or, given `null`, clears the value stored under [NAVIGATION_CONTEXT_KEY].
   *
   * Implementations should clear via [IScope.setContexts] rather than [IScope.removeContexts], as
   * the latter doesn't notify scope observers and therefore doesn't persist the removal to disk.
   */
  fun setNavigationContext(value: Any?)

  /** Replaces the scope's propagation context with a new one. */
  fun rotatePropagationContext()

  /**
   * Replaces the request kept from the most recent [startNavTransaction] with [request] (e.g., to
   * reflect a back stack whose entries below the top changed), so that transactions started for the
   * same destination later are started from it. A transaction that has already started keeps what
   * it was started with.
   *
   * Does nothing if no request is kept, i.e., none was made since the last [finishNavTransaction].
   */
  fun updateNavTransactionRequest(request: NavTransactionRequest)

  /**
   * Starts a nav transaction for [request] and binds it to the scope, unless another unfinished
   * transaction is already bound to it, in which case none is started.
   *
   * (Note: Callers must first [finish][finishNavTransaction] the nav transaction most recently
   * requested, if any. Otherwise, with the nav lease, it blocks the new one while it's bound;
   * without the lease, it's replaced without being finished.)
   *
   * The start may be deferred until the nav lease is acquired, in which case whether another
   * transaction blocks it is decided then (see [StagedNavTransaction.publishTo]). Either way, the
   * request is kept until the next [finishNavTransaction], so that a new transaction can be started
   * from it if the user returns to the nav root after this one finished.
   */
  fun startNavTransaction(request: NavTransactionRequest)

  /**
   * Finishes the nav transaction most recently requested via [startNavTransaction] and unbinds it
   * from the shared [IScope].
   *
   * Alternatively, discards the most recently requested transaction if it's still deferred.
   */
  fun finishNavTransaction()
}
