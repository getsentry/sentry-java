package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.PropagationContext
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App
import io.sentry.protocol.Contexts

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
 * LifecycleOwner (e.g., Activity or Fragment)
 *       ↓ owns
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
 * `NavScopes` instances without a lease write to their own copy of [stagedNavData], and keep their
 * unfinished nav transaction in [stagedNavTransaction], which lets them preserve their latest
 * updates without overwriting another instance's transaction or published data. When an instance
 * acquires the lease, it writes its staged data to the shared `IScope` and binds its unfinished
 * transaction again (subject to any publishing conditions, e.g., the transaction stays unbound if
 * another transaction is already bound to the shared `IScope`).
 *
 * **Usage**
 *
 * 1. Create a `NavScopes` instance per `SentryNavEffect`.
 *
 * 2. Pass the same [NavLeaseCoordinator] instance to all `NavScopes` whose effects must be
 *    coordinated (i.e., that target the same `IScope`).
 *
 * 3. Call [attach] with the lifecycle that manages the effect before the first back stack update.
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
   * Registers this [NavScopes] instance with [lifecycle]. No-ops if `lifecycle` has already been
   * attached or is [destroyed][Lifecycle.Event.ON_DESTROY].
   *
   * Attaching to a second, different lifecycle no-ops.
   */
  fun attach(lifecycle: Lifecycle) {
    val currentLifecycle = attachedLifecycle

    if (currentLifecycle != null) {
      if (currentLifecycle !== lifecycle) {
        delegate.options.logger.log(
          SentryLevel.WARNING,
          "NavScopes is already attached to a different lifecycle; ignoring the new lifecycle.",
        )
      }

      // No-op if already attached to a lifecycle instance (whether the same or a different one).
      return
    }

    if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
      return
    }

    attachedLifecycle = lifecycle
    registration = coordinator.register(lifecycle, delegate, navStatePublisher)
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
  private inner class NavStatePublisher : LeasedNavState {

    override fun publishTo(scope: IScope) {
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

    /**
     * Copies the whole of [IScope.getContexts] in order to return a copy of its [App].
     *
     * [App]'s copy constructor isn't public, so an `App` can't be copied on its own. Going through
     * [Contexts] deep-copies every context on the scope and discards all of it but the `App`.
     */
    override val app: App?
      get() = Contexts(delegate.contexts).app

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

    /**
     * Starts and binds a nav transaction with the nav lease (see [StagedNavTransaction]). Callers
     * must [finish][finishNavTransaction] the previous nav transaction first.
     *
     * Without the lease, starts none and defers the propagation context rotation a new transaction
     * would have implied until the lease is acquired. A transaction started without the lease
     * couldn't be bound to the scope, so its destination's spans would attach to the leaseholder's
     * bound transaction instead, and the empty transaction would be dropped.
     */
    override fun startNavTransaction(
      transactionContext: TransactionContext,
      transactionOptions: TransactionOptions,
    ): INavTransaction? {
      return if (hasLease) {
        stagedNavTransaction.start(delegate, transactionContext, transactionOptions)
      } else {
        stagedNavData.deferPropagationContextRotation()
        null
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
   * A copy of the scope's app context, if any.
   *
   * A copy rather than the live instance, because callers mutate the result before binding it to a
   * transaction, and mutating the scope's own [App] would corrupt shared scope state.
   *
   * Implementations may have to copy more than the [App] to produce it, so treat this as expensive:
   * read it once where it's needed rather than per frame or in a loop.
   */
  val app: App?

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
   * Starts a nav transaction and binds it to the scope, unless another unfinished transaction is
   * already bound to it or the nav lease isn't held. Returns `null` if no transaction was started.
   *
   * (Note: Callers must first [finish][finishNavTransaction] the nav transaction most recently
   * started, if any. Otherwise, it blocks the new one while it's bound.)
   */
  fun startNavTransaction(
    transactionContext: TransactionContext,
    transactionOptions: TransactionOptions,
  ): INavTransaction?

  /**
   * Finishes the nav transaction most recently started via [startNavTransaction] and unbinds it
   * from the shared [IScope].
   */
  fun finishNavTransaction()
}
