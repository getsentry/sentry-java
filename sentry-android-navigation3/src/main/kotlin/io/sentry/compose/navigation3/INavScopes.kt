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

/**
 * An [IScopes] implementation that lets us coordinate updates to navigation state across multiple
 * [SentryNavEffect]s. (Also supports updates managed by a single `SentryNavEffect`.)
 *
 * Needed because all `SentryNavEffect`s mutate a single [IScope] instance by default:
 * ```
 * SentryNavEffect
 *       ↓ owns
 *   NavSession
 *    ├── BackStackObserver — computes nav data and nav transactions
 *    └── INavScopes        — controls whether those results reach the real scope
 *               ↓
 *        NavLeaseCoordinator — arbitrates ownership across all effects
 *               ↓
 *          shared IScope
 * ```
 *
 * The ownership policy differs between nav transactions vs nav data, and is based on the lifetime
 * of the [LifecycleOwner] managing each `SentryNavEffect`:
 * ```
 *   ┌──────── Transaction lease lifetime ────────┐
 *   │                                            │
 *   │  ON_CREATE                                 │
 *   │      │                                     │
 *   │      ▼                                     │
 *   │  ┌──────── Data lease lifetime ─────────┐  │
 *   │  │ ON_RESUME                            │  │
 *   │  │     │                                │  │
 *   │  │     ▼                                │  │
 *   │  │ ON_PAUSE                             │  │
 *   │  └──────────────────────────────────────┘  │
 *   │      │                                     │
 *   │      ▼                                     │
 *   │  ON_DESTROY                                │
 *   │                                            │
 *   └────────────────────────────────────────────┘
 * ```
 *
 * Transaction ownership changes quickly if two `SentryNavEffect`s overlap while the host app
 * transitions between their `LifecycleOwner`s. That lets us ensure no spans are lost and that the
 * incoming destination is available to parent any spans produced by the incoming `LifecycleOwner`.
 *
 * Data ownership, by contrast, requires less internal coordination, as it tracks `LifecycleOwner`
 * visibility.
 *
 * ```
 *   Time
 *    │
 *    ▼
 *
 *   SentryNavEffect A                  Lease owner              SentryNavEffect B
 *   ─────────────────              ───────────────────          ─────────────────
 *
 *   ON_CREATE ───────────────────► Transaction: A
 *       │                          Data:        none
 *       │
 *   ON_RESUME ───────────────────► Transaction: A
 *       │                          Data:        A
 *       │
 *   ON_PAUSE ────────────────────► Transaction: A
 *       │                          Data:        none
 *       │
 *       │                                                            ON_CREATE
 *       │                          Transaction: B ◄───────────────────────┤
 *       │                          Data:        none                      │
 *       │                                                                 │
 *       │                                                            ON_RESUME
 *       │                          Transaction: B ◄───────────────────────┤
 *       │                          Data:        B                         │
 *       │                                                                 │
 *   ON_DESTROY ──────────────────► Transaction: B                         │
 *                                  Data:        B                         │
 *                                  (A owns neither,                       │
 *                                   so B is unaffected)                   │
 *                                                                         │
 *                                                                      ON_PAUSE
 *                                  Transaction: B ◄───────────────────────┤
 *                                  Data:        none                      │
 *                                                                         │
 *                                                                     ON_DESTROY
 *                                  Transaction: none ◄────────────────────┘
 *                                  Data:        none
 * ```
 *
 * **Limitations**
 *
 * Multi-effect support is limited to scenarios where:
 *
 * - each `SentryNavEffect` is managed by a single [LifecycleOwner] (e.g., a multi-Activity app with
 *   one `SentryNavEffect` per Activity);
 *
 * - normally only one `SentryNavEffect` is active at a time; but
 *
 * - brief windows exist where multiple `SentryNavEffect`s may be simultaneously active (e.g., as
 *   the host app switches between activities).
 *
 * Does ***not*** support scenarios where multiple `SentryNavEffect`s are meant to be simultaneously
 * active, whether nested or as siblings (e.g.,
 * [multi-resume mode](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)
 * across two app activities).
 *
 * **Only leaseholders generate nav transactions or data**
 *
 * This class works by maintaining separate nav transaction and nav data leases, and only allowing
 * `INavScopes` instances holding the relevant lease to mutate the underlying `IScope`.
 *
 * `NavIScope` instances without a lease write to a [stagedNavData] holder, which lets them preserve
 * their latest nav updates without overwriting another instance's published data. When an instance
 * acquires the lease, it publishes its staged screen and navigation context to the underlying
 * [IScope].
 *
 * The data lease is acquired automatically as a `LifecycleOwner` transitions into
 * [resumed][Lifecycle.Event.ON_RESUME] state, and released automatically as it transitions into
 * [paused][Lifecycle.Event.ON_PAUSE] state.
 *
 * The transaction lease is acquired during [attach] and in [ON_CREATE][Lifecycle.Event.ON_CREATE]
 * in order to eagerly create a nav transaction for the initial nav destination. (Otherwise initial
 * spans may be dropped or misattributed.) The transaction lease is released upon
 * [disposal][dispose], or whenever another `INavScopes` instance claims ownership.
 *
 * **Usage**
 *
 * 1. Create an `INavScopes` instance per `SentryNavEffect`.
 *
 * 2. Pass the same [NavLeaseCoordinator] instance to all `INavScopes` whose effects must be
 *    coordinated (i.e., that target the same `IScope`).
 *
 * 3. Call [attach] with the lifecycle that manages the effect before the first back stack update.
 *
 * 4. Route back stack updates through [runBackStackUpdate].
 *
 * 5. Call [dispose] when the effect leaves the composition.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Lifecycle callbacks and method invocations should be
 * confined to the same thread.
 */
internal class INavScopes(
  private val delegate: IScopes,
  private val coordinator: NavLeaseCoordinator = defaultNavLeaseCoordinator,
) : IScopes by delegate, LifecycleEventObserver {

  private val owner: Any = Any()
  private var attachedLifecycle: Lifecycle? = null
  private val stagedNavData = StagedNavData()

  /**
   * Whether [runBackStackUpdate] is currently executing its callback.
   *
   * While true, [configureScope] prepares transaction handoff before applying scope updates if this
   * [INavScopes] instance owns the transaction lease. Ordinary scope operations, including observer
   * cleanup, do not trigger that handoff.
   */
  private var isProcessingBackStackUpdate: Boolean = false

  /**
   * Attaches this [INavScopes] instance to [lifecycle] and claims any applicable leases. No-ops if
   * `lifecycle` has already been attached or is [destroyed][Lifecycle.Event.ON_DESTROY].
   *
   * Throws if a caller tries to attach this instance to more than one lifecycle.
   */
  fun attach(lifecycle: Lifecycle) {
    check(attachedLifecycle == null || attachedLifecycle === lifecycle) {
      "INavScopes has already been attached to a different lifecycle!"
    }

    if (attachedLifecycle === lifecycle) {
      return
    }
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
      return
    }

    attachedLifecycle = lifecycle

    coordinator.claimTransactionLease(owner)
    if (lifecycle.currentState == Lifecycle.State.RESUMED) {
      claimDataLease(owner)
    }

    lifecycle.addObserver(this)
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    when (event) {
      Lifecycle.Event.ON_CREATE -> coordinator.claimTransactionLease(owner)
      Lifecycle.Event.ON_RESUME -> claimDataLease(owner)
      Lifecycle.Event.ON_PAUSE -> coordinator.releaseDataLease(owner)
      Lifecycle.Event.ON_DESTROY -> dispose()
      else -> Unit
    }
  }

  /** Runs [block] as a back stack update eligible to replace the previous owner's transaction. */
  fun runBackStackUpdate(block: () -> Unit) {
    isProcessingBackStackUpdate = true
    try {
      block()
    } finally {
      isProcessingBackStackUpdate = false
    }
  }

  /** Releases the leases held by this [INavScopes] instance and any transaction it still owns. */
  fun dispose() {
    attachedLifecycle?.removeObserver(this)
    attachedLifecycle = null

    coordinator.releaseDataLease(owner)

    delegate.configureScope { scope ->
      coordinator.stopOwnedTransaction(owner, scope)
      coordinator.releaseTransactionLease(owner)
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

  override fun startTransaction(
    transactionContext: TransactionContext,
    transactionOptions: TransactionOptions,
  ): ITransaction {
    if (!coordinator.hasTransactionLease(owner)) {
      return NoOpTransaction.getInstance()
    }

    return delegate.startTransaction(transactionContext, transactionOptions).also { transaction ->
      if (!transaction.isNoOp) {
        coordinator.bindTransaction(owner, transaction)
      }
    }
  }

  override fun addBreadcrumb(breadcrumb: Breadcrumb, hint: Hint?) {
    stagedNavData.addBreadcrumb(coordinator.hasDataLease(owner)) {
      delegate.addBreadcrumb(breadcrumb, hint)
    }
  }

  override fun addBreadcrumb(breadcrumb: Breadcrumb) {
    stagedNavData.addBreadcrumb(coordinator.hasDataLease(owner)) {
      delegate.addBreadcrumb(breadcrumb)
    }
  }

  /**
   * Claims the data lease on behalf of this instance, replacing the previous owner.
   *
   * Publishes the staged screen and navigation context, clearing either field with no staged
   * update. Also publishes the latest breadcrumb buffered before this instance's first publication.
   *
   * No-ops if this instance already holds the lease.
   */
  private fun claimDataLease(owner: Any) {
    if (!coordinator.claimDataLease(owner)) {
      return
    }

    delegate.configureScope { scope ->
      if (!coordinator.hasDataLease(owner)) {
        return@configureScope
      }
      stagedNavData.publishTo(scope)
    }

    stagedNavData.publishPendingBreadcrumb(coordinator.hasDataLease(owner))
  }

  private inner class LeasedNavigationScope(private val realScope: IScope) : IScope by realScope {

    override fun getScreen(): String? =
      stagedNavData.getScreen(realScope, coordinator.hasDataLease(owner))

    override fun setScreen(screen: String?) {
      stagedNavData.setScreen(screen, realScope, coordinator.hasDataLease(owner))
    }

    override fun setContexts(key: String?, value: Any?) {
      if (key != NAVIGATION_CONTEXT_KEY) {
        realScope.setContexts(key, value)
        return
      }

      stagedNavData.setNavigationContext(value, realScope, coordinator.hasDataLease(owner))
    }

    override fun getPropagationContext(): PropagationContext =
      if (coordinator.hasTransactionLease(owner)) {
        realScope.propagationContext
      } else {
        stagedNavData.propagationContext
      }

    override fun setPropagationContext(propagationContext: PropagationContext) {
      if (coordinator.hasTransactionLease(owner)) {
        realScope.propagationContext = propagationContext
      } else {
        stagedNavData.propagationContext = propagationContext
      }
    }

    override fun withPropagationContext(
      callback: Scope.IWithPropagationContext
    ): PropagationContext =
      if (coordinator.hasTransactionLease(owner)) {
        realScope.withPropagationContext(callback)
      } else {
        stagedNavData.withPropagationContext(callback)
      }
  }
}

/** Holds navigation data staged for publication. */
private class StagedNavData {

  private val scope = Scope(SentryOptions.empty())

  /**
   * Whether this instance has received a screen update, including an explicit clear.
   *
   * This determines whether [publishTo] should publish the staged screen or clear a previous
   * leaseholder's screen. A null staged value alone cannot distinguish an explicit clear from a
   * screen that has never been updated.
   */
  private var hasScreenUpdate = false

  /**
   * Whether this instance has received a navigation context update, including an explicit clear.
   *
   * This determines whether [publishTo] should publish the staged context or clear a previous
   * leaseholder's context, even if the staged scope no longer contains the key.
   */
  private var hasNavigationContextUpdate = false

  /**
   * The latest breadcrumb received i) before the first breadcrumb is published and ii) while not
   * holding the data lease.
   *
   * This class implements a breadcrumb buffering policy that:
   *
   * 1. tracks the latest breadcrumb received prior to initial data lease acquisition;
   * 2. publishes that breadcrumb upon initial lease acquisition; and
   * 3. discards all breadcrumbs received after its first breadcrumb has been published when the
   *    data lease isn't held.
   *
   * That policy gives us the following behavior:
   *
   * - Suppose a [SentryNavEffect] receives its initial back stack update before its
   *   [LifecycleOwner]'s state reaches [resumed][Lifecycle.Event.ON_RESUME], so it can't publish
   *   its breadcrumb yet. If the effect receives a `Home` and then a `Profile` back stack entry
   *   during that time, buffering the latest breadcrumb lets us publish only `Profile` when the
   *   lease is acquired, which matches what the user actually sees (rather than publishing both
   *   `Home` and `Profile`).
   *
   * - Suppose the `SentryNavEffect` that publishes `Profile` pauses and loses the data lease.
   *   Another effect takes over and records a `Settings` back stack entry. If the paused effect
   *   buffers new breadcrumbs while it lacks the lease, it might later replay (an unseen)
   *   `Checkout` when it resumes, even though `Settings` was already reported by the active effect.
   *   Discarding those later breadcrumbs avoids replaying history that may be stale or out of
   *   order.
   */
  private var pendingInitialBreadcrumb: (() -> Unit)? = null

  /**
   * Whether this instance has ever published a breadcrumb.
   *
   * This remains true across pauses, so breadcrumbs received without the data lease after the first
   * publication are discarded rather than replayed as stale navigation history on resume.
   */
  private var hasPublishedBreadcrumb: Boolean = false

  private val screenName: String?
    get() = scope.screen

  private val contexts
    get() = scope.contexts

  var propagationContext: PropagationContext
    get() = scope.propagationContext
    set(value) {
      scope.propagationContext = value
    }

  fun getScreen(realScope: IScope, ownsDataLease: Boolean): String? =
    if (ownsDataLease) realScope.screen else screenName

  fun setScreen(screen: String?, realScope: IScope, ownsDataLease: Boolean) {
    scope.screen = screen
    hasScreenUpdate = true

    if (ownsDataLease) {
      realScope.screen = screen
    }
  }

  fun setNavigationContext(value: Any?, realScope: IScope, ownsDataLease: Boolean) {
    scope.setContexts(NAVIGATION_CONTEXT_KEY, value)
    hasNavigationContextUpdate = true

    if (ownsDataLease) {
      realScope.setContexts(NAVIGATION_CONTEXT_KEY, value)
    }
  }

  fun publishTo(realScope: IScope) {
    // A new leaseholder publishes a complete navigation state. Clearing fields it hasn't staged
    // prevents the previous leaseholder's values from lingering after a handoff.
    realScope.screen = if (hasScreenUpdate) screenName else null
    realScope.setContexts(
      NAVIGATION_CONTEXT_KEY,
      if (hasNavigationContextUpdate) contexts[NAVIGATION_CONTEXT_KEY] else null,
    )
  }

  fun withPropagationContext(callback: Scope.IWithPropagationContext): PropagationContext =
    scope.withPropagationContext(callback)

  /** Publishes the breadcrumb when leased, or buffers it until the first publication. */
  fun addBreadcrumb(ownsDataLease: Boolean, publish: () -> Unit) {
    if (ownsDataLease) {
      publish()
      hasPublishedBreadcrumb = true
      pendingInitialBreadcrumb = null
    } else if (!hasPublishedBreadcrumb) {
      // Replace older unpublished breadcrumbs so only the latest is replayed on first lease.
      pendingInitialBreadcrumb = publish
    }
  }

  /** Publishes the buffered initial breadcrumb if this instance currently owns the data lease. */
  fun publishPendingBreadcrumb(ownsDataLease: Boolean) {
    val pending = pendingInitialBreadcrumb ?: return
    if (ownsDataLease) {
      pending()
      hasPublishedBreadcrumb = true
      pendingInitialBreadcrumb = null
    }
  }
}

/** Coordinates leases for navigation state shared by multiple [SentryNavEffect]s. */
@Suppress("TooManyFunctions")
internal class NavLeaseCoordinator {

  /**
   * The owner currently allowed to publish Sentry nav data (e.g., breadcrumbs, screen names, back
   * stack context).
   */
  private var dataOwner: Any? = null

  /** The owner currently allowed to start and manage nav transactions. */
  private var transactionOwner: Any? = null

  /**
   * The owner of [activeTransaction], which may differ from [transactionOwner] during handoff. The
   * previous owner's active transaction remains until the new owner processes a back stack update,
   * at which point it is stopped before the new transaction starts.
   */
  private var activeTransactionOwner: Any? = null

  private var activeTransaction: ITransaction? = null

  /**
   * Checks whether [owner] currently holds the nav data lease.
   *
   * @return `true` if [owner] is the current data owner, otherwise `false`.
   */
  fun hasDataLease(owner: Any): Boolean = dataOwner === owner

  /**
   * Gives [owner] the lease to publish nav data, replacing any previous data owner.
   *
   * @return `true` if the lease was transferred to [owner], or `false` if [owner] already held the
   *   lease.
   */
  fun claimDataLease(owner: Any): Boolean {
    if (dataOwner === owner) {
      return false
    }

    dataOwner = owner
    return true
  }

  /**
   * Releases the nav data lease if it is held by [owner].
   *
   * @return `true` if the lease was released, or `false` if [owner] did not hold it.
   */
  fun releaseDataLease(owner: Any): Boolean {
    if (!hasDataLease(owner)) {
      return false
    }

    dataOwner = null
    return true
  }

  /**
   * Checks whether [owner] currently holds the nav transaction lease.
   *
   * @return `true` if [owner] is the current transaction owner, otherwise `false`.
   */
  fun hasTransactionLease(owner: Any): Boolean = transactionOwner === owner

  /**
   * Gives [owner] the lease to manage nav transactions, replacing any previous owner.
   *
   * @return `true` if the lease was transferred to [owner], or `false` if [owner] already held the
   *   lease.
   */
  fun claimTransactionLease(owner: Any): Boolean {
    if (transactionOwner === owner) {
      return false
    }

    transactionOwner = owner
    return true
  }

  fun bindTransaction(owner: Any, transaction: ITransaction) {
    if (hasTransactionLease(owner)) {
      activeTransactionOwner = owner
      activeTransaction = transaction
    }
  }

  /** Stops the previous owner's transaction when [owner] next processes a back stack update. */
  fun prepareTransactionUpdate(owner: Any, scope: IScope) {
    clearFinishedTransaction()
    if (hasTransactionLease(owner) && activeTransactionOwner !== owner) {
      stopActiveTransaction(scope)
    }
  }

  /** Stops [owner]'s active transaction and clears it from [scope] if it is still current. */
  fun stopOwnedTransaction(owner: Any, scope: IScope) {
    if (activeTransactionOwner === owner) {
      stopActiveTransaction(scope)
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

  fun clearFinishedTransaction() {
    if (activeTransaction?.isFinished == true) {
      activeTransactionOwner = null
      activeTransaction = null
    }
  }

  fun releaseTransactionLease(owner: Any) {
    if (hasTransactionLease(owner)) {
      transactionOwner = activeTransactionOwner
    }
  }
}

internal val defaultNavLeaseCoordinator: NavLeaseCoordinator = NavLeaseCoordinator()
