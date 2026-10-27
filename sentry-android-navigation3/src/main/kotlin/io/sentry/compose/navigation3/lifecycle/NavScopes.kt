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

  /** This instance's staged nav transaction and nav data, as published under the nav lease. */
  private val leasedNavState =
    object : LeasedNavState {

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
          // Without a scope (e.g., the SDK is disabled), the transaction can't be sent and will
          // time out anyway. Not finishing it immediately lets us ensure the error-handling policy
          // from IScopes.configureScope covers all SDK work.
          stagedNavTransaction.forget()
        }
      }
    }

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
    registration = coordinator.register(lifecycle, delegate, leasedNavState)
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
   * An [INavScope] that maintains a [stagedNavTransaction] and [stagedNavData], and only writes to
   * or reads from the [delegate IScope][IScope] when this instance holds the nav lease.
   *
   * A `NavScope` holds no state of its own. What it stages belongs to the enclosing [NavScopes]
   * instance.
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
     * Starts a nav transaction and binds it to the shared [IScope] if this instance holds the nav
     * lease, or defers its start otherwise. First [finishes][finishNavTransaction] the nav
     * transaction most recently requested, if any.
     *
     * With the lease, this method will **not** start a transaction if another unfinished
     * transaction (e.g., the host app's) is already bound to the shared scope, as the new one
     * couldn't be bound in its place. A finished transaction doesn't block.
     *
     * Without the lease, the start is always deferred, whatever is bound to the shared scope at the
     * time: what matters is what's bound when the lease is acquired, which is checked then (see
     * [StagedNavTransaction.publishTo]). Checking now would drop the transaction of an owner that
     * stages it while a short-lived host transaction is bound (e.g., a ViewPager2 page preloaded
     * during an Activity's `ui.load` transaction), even if that transaction finishes long before
     * the owner acquires the lease.
     *
     * **Deferred transactions may mis-parent spans generated before lease acquisition**
     *
     * A deferred transaction starts when the lease is acquired, which may be later than when its
     * destination starts doing work (e.g., ViewPager2 can precompose offscreen pages, or an
     * Activity that navigates from a result callback before it resumes). Spans created in the
     * meantime attach to whichever transaction is bound to the scope at that time, usually the
     * leaseholder's.
     *
     * If the destination creates no spans once its transaction starts, the idle transaction is
     * dropped once it finishes.
     */
    override fun startNavTransaction(
      transactionContext: TransactionContext,
      transactionOptions: TransactionOptions,
    ): INavTransaction? {
      // StagedNavTransaction replaces its current nav transaction without finishing it, which
      // would leave that one running with no way to finish it later, so we do so here.
      finishNavTransaction()

      return if (hasLease) {
        if (!isIScopeTransactionSlotFree(transactionContext.name)) {
          return null
        }
        stagedNavTransaction.start(delegate, transactionContext, transactionOptions)
      } else {
        stagedNavData.deferPropagationContextRotation()
        stagedNavTransaction.stage(transactionContext, transactionOptions)
      }
    }

    override fun finishNavTransaction() {
      stagedNavTransaction.finish(delegate)
    }

    /**
     * Returns `true` if the transaction slot in the shared [IScope] is free, i.e., the bound
     * transaction is absent or finished. Otherwise, logs that the nav transaction for [name] won't
     * be created.
     */
    private fun isIScopeTransactionSlotFree(name: String): Boolean {
      val currentTransaction = delegate.transaction
      if (currentTransaction == null || currentTransaction.isFinished) {
        return true
      }

      options.logger.log(
        SentryLevel.DEBUG,
        "Nav transaction for route %s won't be created because another transaction is active.",
        name,
      )
      return false
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
   * already bound to it. Returns `null` if no transaction was started. First
   * [finishes][finishNavTransaction] the nav transaction most recently requested, if any.
   *
   * The start may be deferred until the nav lease is acquired, in which case writes to the returned
   * transaction are staged until then, and whether another transaction blocks it is decided then
   * (see [NavScopes.NavScope.startNavTransaction]).
   */
  fun startNavTransaction(
    transactionContext: TransactionContext,
    transactionOptions: TransactionOptions,
  ): INavTransaction?

  /**
   * Finishes the nav transaction most recently requested via [startNavTransaction] and unbinds it
   * from the shared [IScope].
   *
   * Alternatively, discards the most recently requested transaction if it's still deferred.
   */
  fun finishNavTransaction()
}
