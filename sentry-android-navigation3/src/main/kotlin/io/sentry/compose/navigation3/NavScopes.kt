package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_PAUSE
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
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
import io.sentry.SentryLevel
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
 *    └── NavScopes         — controls whether those results reach the real scope
 *               ↓
 *        NavLeaseCoordinator — arbitrates ownership across all effects
 *               ↓
 *          shared IScope
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
 * This class does ***not*** support scenarios where multiple `SentryNavEffect`s are meant to be
 * simultaneously active, whether nested or as siblings (e.g.,
 * [multi-resume mode](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)
 * across two app activities).
 *
 * `NavScopes` is not a general-purpose lease-protected `IScopes` implementation. Ways of accessing
 * or changing an `IScope` beyond those currently used by `SentryNavEffect` are not necessarily
 * protected. New operations must either stay within the current use cases or extend lease
 * protection to cover them.
 *
 * **A fundamental tradeoff**
 *
 * During `LifecycleOwner` overlap, the current transaction and scope can reflect **_either_**:
 *
 * 1. the outgoing owner that's still visible (in `STARTED` state after pausing), or
 * 2. the incoming owner that may crash before it becomes visible (in `CREATED` state)
 *
 * but not both. We've opted for (2) so that incoming destination populates crash context and work
 * performed is associated with it†, as start-up logic is more likely to be the source of a crash or
 * interesting spans than the outgoing destination's steady state. (If the incoming owner aborts
 * prematurely, the nav transaction and data for the most recently resumed owner still in at least
 * `STARTED` state is restored.)
 *
 * † Associating work with the incoming transaction means spans produced by the outgoing owner may
 * appear as if they were produced by the incoming owner (or vice versa, were our policy reversed).
 * A possible solution would be to implement a parent span delivery system similar to
 * [LocalSentrySpan], but that's a task for another day.
 *
 * **Only leaseholders generate nav transactions and nav data**
 *
 * This class works by only allowing `NavScopes` instances holding a lease to publish their
 * navigation data and transactions to the underlying `IScope`.
 *
 * `NavScopes` instances without a lease write to a [stagedNavData] holder, which lets them preserve
 * their latest updates without overwriting another instance's published data. When an instance
 * acquires the lease, it publishes its staged data to the underlying `IScope`.
 *
 * **Lease handoff policy**
 *
 * The lease is acquired preemptively by an incoming `LifecycleOwner` when it applies its first
 * [back stack update][runBackStackUpdate].
 *
 * The displaced owner (if any) remains eligible for restoration until it stops. Pausing releases
 * the lease but preserves eligibility. If the incoming owner aborts before the displaced owner
 * stops, the lease reverts to the displaced owner and its nav data and unfinished transaction are
 * restored.
 *
 * The following diagram illustrates how handoff works:
 * ```
 *  Time
 *    │
 *    ▼
 *
 * SentryNavEffect A              Shared state                SentryNavEffect B
 * ─────────────────             ────────────                 ─────────────────
 *
 * attach()                      Navigation lease: none
 *   │
 * Initial back stack update
 *   │
 *   ├── acquire lease ────────► Navigation lease: A
 *   ├── create & bind txA ────► Scope transaction: txA
 *   ├── publish nav data ─────► Published data: A
 *   │
 * ON_RESUME                    A is eligible for restoration
 *   │
 * ON_PAUSE
 *   │
 *   ├────────────────────────► Navigation lease: none
 *   │                          txA and published data remain
 *   │                          A remains eligible for restoration
 *   │
 *   │                                                                attach()
 *   │                                                                   │
 *   │                          Navigation lease: none ◄─────────────────┤
 *   │                          Scope transaction: txA                   │
 *   │                          Published data: A                        │
 *   │                          B registered, awaiting initial back      │
 *   │                            stack update                           │
 *   │                                                                   │
 *   │                                                   Initial back stack update
 *   │                                                                   │
 *   │                                         suspend & unbind txA ─────┤
 *   │                          Navigation lease: B ◄── acquire lease ───┤
 *   │                          Scope transaction: txB ◄── bind txB ─────┤
 *   │                          Published data: B ◄── publish nav data ──┤
 *   │                          (rollback point)                         │
 *   │                                                                   │
 *   │                                                              ON_RESUME
 *   │                                                                   │
 *   │                          B eligible for restoration ◄─────────────┤
 *   │                                                                   │
 * ON_STOP                                                               │
 *   │                                                                   │
 *   ├── retain txA; timers continue                                     │
 *   ├────────────────────────► A no longer eligible for restoration     │
 *   │                                                                   │
 * ON_DESTROY                                                            │
 *   │                                                                   │
 *   ├── finish retained txA if unfinished                               │
 *   └────────────────────────► A no longer tracked                      │
 *                                                                   ON_PAUSE
 *                                                                       │
 *                              Navigation lease: none ◄─────────────────┤
 *                              Published data: B remains                │
 *                              Existing txB may finish naturally        │
 *                              No new transactions or publication by B  │
 *                                                                       │
 *                                                                   ON_STOP
 *                                                                       │
 *                              B no longer eligible for restoration ◄───┤
 *                                                                       │
 *                                                                 ON_DESTROY
 *                                                                       │
 *                              Finish & clear txB if still bound ◄──────┤
 *                              Clear B's remaining screen/back stack    │
 *                              Retain recorded breadcrumbs              │
 *                                                                       ▼
 *
 * Alternative at rollback point: B disposes before A stops
 *                              Finish & unbind txB ◄────── dispose B ───┤
 *                              Navigation lease: A                      │
 *                              Restore txA if unfinished and scope free │
 *                              Republish A's staged data                │
 * ```
 *
 * **Encapsulation policy**
 *
 * This class and [NavLeaseCoordinator] are meant to fully encapsulate knowledge about the
 * possibility of multiple `SentryNavEffect`s and the shared mutable state issues it raises.
 * Addressing those concerns is complicated – don't let that complexity escape into the rest of the
 * `SentryNavEffect` system!
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
 * 4. Route back stack updates through [runBackStackUpdate].
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
  private val coordinator: NavLeaseCoordinator = defaultNavLeaseCoordinator,
) : IScopes by delegate, LifecycleEventObserver {

  private val owner: Any = Any()
  private var attachedLifecycle: Lifecycle? = null
  private val stagedNavData = StagedNavData()

  /**
   * Whether [runBackStackUpdate] is processing a back-stack update.
   *
   * This flag distinguishes a [back stack update][runBackStackUpdate] from other requests that also
   * invoke [configureScope]. Needed in order to transfer lease ownership for the former but not the
   * latter.
   */
  private var isProcessingBackStackUpdate: Boolean = false

  /**
   * Registers this [NavScopes] instance with [lifecycle]. No-ops if `lifecycle` has already been
   * attached or is [destroyed][Lifecycle.Event.ON_DESTROY].
   *
   * Attaching to a second, different lifecycle is unsupported; it's logged and ignored.
   */
  fun attach(lifecycle: Lifecycle) {
    val currentLifecycle = attachedLifecycle
    if (currentLifecycle != null && currentLifecycle !== lifecycle) {
      delegate.options.logger.log(
        SentryLevel.WARNING,
        "NavScopes is already attached to a different lifecycle; ignoring the new lifecycle.",
      )
      return
    }

    if (currentLifecycle === lifecycle) {
      return
    }
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
      return
    }

    attachedLifecycle = lifecycle

    coordinator.registerCandidate(
      owner = owner,
      lifecycleState = lifecycle.currentState,
      onPublishData = ::publishStagedNavData,
      onClearPublishedData = stagedNavData::clearPublishedStateFrom,
    )

    lifecycle.addObserver(this)
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    when (event) {
      ON_START -> coordinator.startCandidate(owner)
      ON_RESUME -> withScopeOrNull { coordinator.resumeCandidate(owner, it) }
      ON_PAUSE -> withScopeOrNull { coordinator.pauseCandidate(owner, it) }
      ON_STOP -> withScopeOrNull { coordinator.stopCandidate(owner, it) }
      ON_DESTROY -> dispose()
      else -> Unit
    }
  }

  /**
   * Runs [block] as a back stack update eligible to acquire the navigation lease.
   *
   * The observer prepares its update before invoking [configureScope], which acquires the lease
   * immediately before application. Preparation failures leave the previous owner intact.
   */
  fun runBackStackUpdate(block: () -> Unit) {
    isProcessingBackStackUpdate = true
    try {
      block()
    } finally {
      isProcessingBackStackUpdate = false
    }
  }

  override fun configureScope(scopeType: ScopeType?, callback: ScopeCallback) {
    val callback = ScopeCallback { scope ->
      if (isProcessingBackStackUpdate) {
        coordinator.onBackStackUpdate(owner, scope)
      }
      callback.run(LeaseProtectedNavScope(scope, owner, stagedNavData, coordinator))
    }

    if (scopeType == null) {
      delegate.configureScope(callback)
    } else {
      delegate.configureScope(scopeType, callback)
    }
  }

  override fun startTransaction(
    transactionContext: TransactionContext,
    transactionOptions: TransactionOptions,
  ): ITransaction {
    if (!coordinator.hasLease(owner)) {
      return NoOpTransaction.getInstance()
    }

    return delegate.startTransaction(transactionContext, transactionOptions).also { transaction ->
      if (!transaction.isNoOp) {
        coordinator.bindTransaction(owner, transaction)
      }
    }
  }

  override fun addBreadcrumb(breadcrumb: Breadcrumb) {
    stagedNavData.addBreadcrumb(coordinator.hasLease(owner)) {
      delegate.addBreadcrumb(breadcrumb)
    }
  }

  override fun addBreadcrumb(breadcrumb: Breadcrumb, hint: Hint?) {
    stagedNavData.addBreadcrumb(coordinator.hasLease(owner)) {
      delegate.addBreadcrumb(breadcrumb, hint)
    }
  }

  /**
   * Releases the lease, clears remaining published state, and finishes this instance's transaction.
   *
   * Also stops observing the attached [LifecycleOwner] and cleans up memory references.
   */
  fun dispose() {
    attachedLifecycle?.removeObserver(this)
    attachedLifecycle = null

    // Clear nav state, unbind the nav transaction, and avoid leaking the LifecycleOwner.
    withScopeOrNull { coordinator.unregisterCandidate(owner, it) }
  }

  /**
   * Runs [block] with the real scope, or with `null` if [configureScope][IScopes.configureScope]
   * skips its callback (e.g., the SDK is disabled), so coordinator bookkeeping still happens.
   *
   * A callback that ran but threw is not retried, as retrying could let its exception escape into
   * the host app.
   */
  private fun withScopeOrNull(block: (IScope?) -> Unit) {
    var callbackInvoked = false
    delegate.configureScope { scope ->
      callbackInvoked = true
      block(scope)
    }

    if (!callbackInvoked) {
      block(null)
    }
  }

  private fun publishStagedNavData() {
    delegate.configureScope { scope ->
      if (!coordinator.hasLease(owner)) {
        return@configureScope
      }
      stagedNavData.publishTo(scope)
    }

    stagedNavData.publishPendingBreadcrumb(coordinator.hasLease(owner))
  }
}

/**
 * Holds navigation data staged for publication.
 *
 * In particular, two types of nav data are tracked:
 *
 * 1. **Nav state**: a screen name and back stack nav context
 * 2. **Nav events**: a pending breadcrumb
 *
 * (Class also tracks propagation context so a non-leaseholder can't overwrite the current
 * leaseholder's tracing state.)
 */
internal class StagedNavData {

  private val scope = Scope(SentryOptions.empty())

  private var hasNavigationContextUpdate = false
  private var hasPropagationContextUpdate = false
  private var hasScreenUpdate = false

  // These may differ from the staged values after background updates. We keep the exact published
  // references so cleanup preserves host replacements, including structurally equal replacements.
  private var publishedNavigationContext: Any? = null
  private var publishedScreen: String? = null

  /**
   * The latest breadcrumb received i) before the first breadcrumb is published and ii) while not
   * holding the navigation lease.
   *
   * This class implements a breadcrumb buffering policy that:
   *
   * 1. tracks the latest breadcrumb received prior to initial navigation lease acquisition;
   * 2. publishes that breadcrumb upon initial lease acquisition; and
   * 3. discards all breadcrumbs received after its first breadcrumb has been published when the
   *    navigation lease isn't held.
   *
   * That policy produces the following behavior:
   *
   * - Breadcrumbs staged before initial lease acquisition retain only the latest destination.
   *   Normally the first prepared update acquires the lease before emitting its breadcrumb, so it
   *   is published immediately.
   *
   * - Suppose the `SentryNavEffect` that publishes `Profile` pauses and loses the navigation lease.
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
   * This remains true across pauses, so breadcrumbs received without the navigation lease after the
   * first publication are discarded rather than replayed as stale navigation history on resume.
   */
  private var hasPublishedBreadcrumb: Boolean = false

  private val screenName: String?
    get() = scope.screen

  private val contexts
    get() = scope.contexts

  var propagationContext: PropagationContext
    get() = scope.propagationContext
    set(value) {
      scope.propagationContext = PropagationContext(value)
      hasPropagationContextUpdate = true
    }

  fun getScreen(realScope: IScope, ownsLease: Boolean): String? =
    if (ownsLease) realScope.screen else screenName

  fun setScreen(screen: String?, realScope: IScope, ownsLease: Boolean) {
    scope.screen = screen
    hasScreenUpdate = true

    if (ownsLease) {
      publishScreen(screen, realScope)
    }
  }

  fun setNavigationContext(value: Any?, realScope: IScope, ownsLease: Boolean) {
    scope.setContexts(NAVIGATION_CONTEXT_KEY, value)
    hasNavigationContextUpdate = true

    if (ownsLease) {
      publishNavigationContext(value, realScope)
    }
  }

  fun withPropagationContext(callback: Scope.IWithPropagationContext): PropagationContext =
    scope.withPropagationContext(callback).also { hasPropagationContextUpdate = true }

  /** Publishes the breadcrumb when leased, or buffers it until the first publication. */
  fun addBreadcrumb(ownsLease: Boolean, publish: () -> Unit) {
    if (ownsLease) {
      publish()
      hasPublishedBreadcrumb = true
      pendingInitialBreadcrumb = null
    } else if (!hasPublishedBreadcrumb) {
      // Replace older unpublished breadcrumbs so only the latest is replayed on first lease.
      pendingInitialBreadcrumb = publish
    }
  }

  fun publishPendingBreadcrumb(ownsLease: Boolean) {
    val pending = pendingInitialBreadcrumb ?: return
    if (ownsLease) {
      pending()
      hasPublishedBreadcrumb = true
      pendingInitialBreadcrumb = null
    }
  }

  fun publishTo(realScope: IScope) {
    // A new leaseholder publishes a complete navigation state. Clearing fields it hasn't staged
    // prevents the previous leaseholder's values from lingering after a handoff.
    publishScreen(if (hasScreenUpdate) screenName else null, realScope)
    publishNavigationContext(
      if (hasNavigationContextUpdate) contexts[NAVIGATION_CONTEXT_KEY] else null,
      realScope,
    )
    if (hasPropagationContextUpdate) {
      realScope.propagationContext = PropagationContext(scope.propagationContext)
    }
  }

  /** Called only for the last publishing session; releasing its lease alone does not clear data. */
  fun clearPublishedStateFrom(realScope: IScope) {
    if (publishedScreen != null && realScope.screen === publishedScreen) {
      realScope.screen = null
    }
    if (
      publishedNavigationContext != null &&
        realScope.contexts[NAVIGATION_CONTEXT_KEY] === publishedNavigationContext
    ) {
      // Use setContexts so scope observers also receive the removal.
      realScope.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
    }
    publishedScreen = null
    publishedNavigationContext = null
  }

  private fun publishScreen(screen: String?, realScope: IScope) {
    publishedScreen = screen
    realScope.screen = screen
  }

  private fun publishNavigationContext(value: Any?, realScope: IScope) {
    publishedNavigationContext = value
    realScope.setContexts(NAVIGATION_CONTEXT_KEY, value)
  }
}

/**
 * An [IScope] wrapper that provides access to [delegate] for nav-related updates only when the
 * current [owner] holds the nav lease; otherwise all nav-related writes and relevant nav-related
 * reads† are redirected to [stagedNavData].
 *
 * All other access is delegated directly to [delegate].
 *
 * † Note that context-related reads aren't currently redirected to [stagedNavData] because
 * implementing that redirection is involved, and we don't currently need it.
 */
internal class LeaseProtectedNavScope(
  private val delegate: IScope,
  private val owner: Any,
  private val stagedNavData: StagedNavData,
  private val coordinator: NavLeaseCoordinator,
) : IScope by delegate {

  override fun getScreen(): String? = stagedNavData.getScreen(delegate, coordinator.hasLease(owner))

  override fun setScreen(screen: String?) {
    stagedNavData.setScreen(screen, delegate, coordinator.hasLease(owner))
  }

  override fun setContexts(key: String?, value: Any?) {
    if (key != NAVIGATION_CONTEXT_KEY) {
      delegate.setContexts(key, value)
      return
    }

    stagedNavData.setNavigationContext(value, delegate, coordinator.hasLease(owner))
  }

  override fun getPropagationContext(): PropagationContext =
    if (coordinator.hasLease(owner)) {
      delegate.propagationContext
    } else {
      stagedNavData.propagationContext
    }

  override fun setPropagationContext(propagationContext: PropagationContext) {
    stagedNavData.propagationContext = propagationContext
    if (coordinator.hasLease(owner)) {
      delegate.propagationContext = propagationContext
    }
  }

  override fun withPropagationContext(callback: Scope.IWithPropagationContext): PropagationContext =
    if (coordinator.hasLease(owner)) {
      delegate.withPropagationContext(callback).also { stagedNavData.propagationContext = it }
    } else {
      stagedNavData.withPropagationContext(callback)
    }
}

/**
 * Manages a nav lease in order to determine which [NavScopes] instance can start nav transactions
 * and publish nav data to a shared [IScope].
 *
 * `NavScopes` instances register themselves as [candidates][Candidate] for lease ownership via
 * [registerCandidate].
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. All `NavScopes` instances using this coordinator must
 * confine their access to the same thread.
 */
@Suppress("TooManyFunctions")
internal class NavLeaseCoordinator {

  private val candidates = mutableListOf<Candidate>()

  /** Candidate whose nav transaction may still be bound to the shared scope. */
  private var activeTransactionCandidate: Candidate? = null

  /** Candidate responsible for the most recently published nav data in the shared scope. */
  private var publishedDataCandidate: Candidate? = null

  private var leaseHolder: Candidate? = null

  /**
   * Counter that orders candidates by when this coordinator most recently observed them in resumed
   * state.
   *
   * Each resume, including registration of an already-resumed owner, assigns the next value to that
   * candidate's [Candidate.resumeOrder]. This lets fallback selection prefer the most recently
   * resumed eligible candidate without relying on clock timestamps. Zero is reserved for candidates
   * that have not been observed resumed and therefore cannot be chosen as fallbacks.
   */
  private var resumeOrder: Long = 0

  fun registerCandidate(
    owner: Any,
    lifecycleState: Lifecycle.State,
    onPublishData: () -> Unit,
    onClearPublishedData: (IScope) -> Unit = {},
  ) {
    if (findCandidate(owner) != null) {
      return
    }

    val initialState =
      when (lifecycleState) {
        Lifecycle.State.INITIALIZED,
        Lifecycle.State.CREATED -> Candidate.LifecycleState.INITIAL_CREATED
        Lifecycle.State.STARTED -> Candidate.LifecycleState.INITIAL_STARTED
        Lifecycle.State.RESUMED -> Candidate.LifecycleState.RESUMED
        Lifecycle.State.DESTROYED -> return
      }

    val initialResumeOrder = if (lifecycleState == Lifecycle.State.RESUMED) ++resumeOrder else 0
    candidates +=
      Candidate(owner, onPublishData, onClearPublishedData, initialState, initialResumeOrder)
  }

  fun startCandidate(owner: Any) {
    findCandidate(owner)?.start()
  }

  /**
   * Records that [owner] resumed and, given a [scope], restores its lease if it has already applied
   * a back stack update.
   *
   * A `null` scope (e.g., the SDK is disabled) still records the lifecycle transition so the
   * candidate remains eligible for fallback once a scope becomes available.
   */
  fun resumeCandidate(owner: Any, scope: IScope?) {
    findCandidate(owner)?.let {
      it.resume(++resumeOrder)

      if (scope != null && it.hasAppliedInitialBackStackUpdate) {
        transferLeaseTo(it, scope)
      }
    }
  }

  /** See [resumeCandidate] for `null` [scope] handling. */
  fun pauseCandidate(owner: Any, scope: IScope?) {
    findCandidate(owner)?.let {
      it.pause()

      if (leaseHolder === it) {
        handOffLease(mostRecentlyResumedCandidate(excluding = it), scope)
      }
    }
  }

  /** See [resumeCandidate] for `null` [scope] handling. */
  fun stopCandidate(owner: Any, scope: IScope?) {
    findCandidate(owner)?.let {
      it.stop()

      if (leaseHolder === it) {
        handOffLease(mostRecentlyResumedCandidate(), scope)
      }
    }
  }

  fun unregisterCandidate(owner: Any, scope: IScope?) {
    findCandidate(owner)?.let {
      finishTransaction(it, scope)
      candidates.remove(it)

      if (publishedDataCandidate === it) {
        publishedDataCandidate = null
        if (scope != null) {
          it.onClearPublishedData(scope)
        }
      }

      if (leaseHolder === it) {
        handOffLease(mostRecentlyResumedCandidate(), scope)
      }
    }
  }

  fun hasLease(owner: Any): Boolean = leaseHolder?.owner === owner

  /**
   * Records that the registered [owner] is about to apply a back-stack update and acquires the nav
   * lease if the owner's lifecycle permits it.
   *
   * If the lease transfers, ownership and transaction binding are updated before the candidate's
   * staged nav data is published. Publication completes before this method returns, so the caller
   * can then apply the back-stack update. Later calls for the same [owner] do nothing.
   */
  fun onBackStackUpdate(owner: Any, scope: IScope) {
    val candidate = findCandidate(owner) ?: return
    if (candidate.hasAppliedInitialBackStackUpdate) {
      return
    }

    candidate.hasAppliedInitialBackStackUpdate = true

    if (candidate.lifecycleState.canAcquireLeaseOnFirstUpdate) {
      transferLeaseTo(candidate, scope)
    }
  }

  fun bindTransaction(owner: Any, transaction: ITransaction) {
    val candidate = leaseHolder ?: return
    if (candidate.owner === owner) {
      candidate.transaction = transaction
      activeTransactionCandidate = candidate
    }
  }

  private fun findCandidate(owner: Any): Candidate? = candidates.firstOrNull { it.owner === owner }

  private fun mostRecentlyResumedCandidate(excluding: Candidate? = null): Candidate? =
    candidates
      .filter {
        it !== excluding &&
          it.hasAppliedInitialBackStackUpdate &&
          it.lifecycleState.isAtLeastStarted &&
          it.resumeOrder > 0
      }
      .maxByOrNull { it.resumeOrder }

  /**
   * Hands the current lease to [next]. Without a [scope], the lease is released instead, as [next]
   * could not publish its staged data.
   */
  private fun handOffLease(next: Candidate?, scope: IScope?) {
    if (scope != null) {
      transferLeaseTo(next, scope)
    } else {
      leaseHolder = null
    }
  }

  private fun transferLeaseTo(candidate: Candidate?, scope: IScope) {
    if (leaseHolder === candidate) {
      return
    }

    if (candidate != null) {
      // Unbind another owner's transaction without finishing it, so it can be restored if that
      // owner returns. If this owner's retained transaction has since finished, discard it
      // instead of binding a finished transaction to the shared scope.
      val previous = activeTransactionCandidate
      if (previous != null && previous !== candidate) {
        clearTransactionFromScope(previous, scope)
        activeTransactionCandidate = null
      }

      val transaction = candidate.transaction
      if (transaction?.isFinished == true) {
        clearTransactionFromScope(candidate, scope)
        candidate.transaction = null
        if (activeTransactionCandidate === candidate) {
          activeTransactionCandidate = null
        }
      } else if (transaction != null) {
        // The navigation lease doesn't give this owner control over an unrelated transaction
        // already bound to the shared scope. Restore the owner's retained nav transaction only if
        // the scope is free or already holds that same transaction.
        scope.withTransaction { current ->
          if (current == null || current === transaction) {
            scope.transaction = transaction
            activeTransactionCandidate = candidate
          }
        }
      }
    }

    leaseHolder = candidate
    if (candidate != null) {
      publishedDataCandidate = candidate
      candidate.onPublishData()
    }
  }

  private fun finishTransaction(candidate: Candidate, scope: IScope?) {
    val transaction = candidate.transaction
    if (transaction != null && !transaction.isFinished) {
      transaction.finish(transaction.status ?: SpanStatus.OK)
    }

    if (scope != null) {
      clearTransactionFromScope(candidate, scope)
    }

    candidate.transaction = null

    if (activeTransactionCandidate === candidate) {
      activeTransactionCandidate = null
    }
  }

  private fun clearTransactionFromScope(candidate: Candidate, scope: IScope) {
    val transaction = candidate.transaction ?: return
    scope.withTransaction { current ->
      if (current === transaction) {
        scope.clearTransaction()
      }
    }
  }

  /** A [LifecycleOwner] that has registered itself as a candidate for lease ownership. */
  private class Candidate(
    val owner: Any,
    val onPublishData: () -> Unit,
    val onClearPublishedData: (IScope) -> Unit,
    initialLifecycleState: LifecycleState,
    initialResumeOrder: Long,
  ) {

    /** Lifecycle state used to decide when a candidate may acquire the nav lease. */
    enum class LifecycleState {

      /**
       * The candidate is in CREATED state but has yet to reach an observed STARTED state.
       *
       * "Initial" refers to the candidate's lifecycle history since being
       * [registered][NavLeaseCoordinator.registerCandidate], and not necessarily to the candidate's
       * lifecycle prior to registration.
       */
      INITIAL_CREATED,

      /**
       * The candidate is STARTED, but it has yet to reach an observed RESUMED state.
       *
       * "Initial" refers to the candidate's lifecycle history since being
       * [registered][NavLeaseCoordinator.registerCandidate], and not necessarily to the candidate's
       * lifecycle prior to registration.
       */
      INITIAL_STARTED,
      RESUMED,
      PAUSED,
      STOPPED,

      /**
       * The candidate has returned to STARTED state after a being in an observed STOPPED state, but
       * has not yet RESUMED.
       *
       * Unlike [INITIAL_STARTED], restarting does not allow a first back-stack update to take the
       * nav lease from another owner.
       */
      RESTARTED;

      /**
       * Whether this lifecycle state permits the candidate's first back stack update since
       * [registration][NavLeaseCoordinator.registerCandidate] to acquire the nav lease, including
       * taking it from another owner.
       *
       * This checks lifecycle permission only; it does not indicate whether the candidate has
       * already applied its first update.
       */
      val canAcquireLeaseOnFirstUpdate: Boolean
        get() =
          when (this) {
            INITIAL_CREATED,
            INITIAL_STARTED,
            RESUMED -> true

            PAUSED,
            STOPPED,
            RESTARTED -> false
          }

      val isAtLeastStarted: Boolean
        get() =
          when (this) {
            INITIAL_CREATED,
            STOPPED -> false

            INITIAL_STARTED,
            RESUMED,
            PAUSED,
            RESTARTED -> true
          }
    }

    var lifecycleState: LifecycleState = initialLifecycleState
      private set

    var transaction: ITransaction? = null

    /** See [NavLeaseCoordinator.resumeOrder]. */
    var resumeOrder: Long = initialResumeOrder
      private set

    var hasAppliedInitialBackStackUpdate: Boolean = false

    fun start() {
      lifecycleState =
        when (lifecycleState) {
          LifecycleState.INITIAL_CREATED,
          LifecycleState.INITIAL_STARTED -> LifecycleState.INITIAL_STARTED

          LifecycleState.STOPPED,
          LifecycleState.RESTARTED -> LifecycleState.RESTARTED
          // Attaching to an already-resumed lifecycle replays ON_START before ON_RESUME.
          LifecycleState.RESUMED -> LifecycleState.RESUMED
          LifecycleState.PAUSED -> LifecycleState.PAUSED
        }
    }

    fun resume(order: Long) {
      resumeOrder = order
      lifecycleState = LifecycleState.RESUMED
    }

    fun pause() {
      lifecycleState = LifecycleState.PAUSED
    }

    fun stop() {
      lifecycleState = LifecycleState.STOPPED
    }
  }
}

internal val defaultNavLeaseCoordinator: NavLeaseCoordinator = NavLeaseCoordinator()
