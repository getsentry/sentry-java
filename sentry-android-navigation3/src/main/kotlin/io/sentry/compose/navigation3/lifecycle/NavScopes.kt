package io.sentry.compose.navigation3.lifecycle

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
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions

/** The key under which nav context (i.e., the captured back stack) is stored in a scope. */
internal const val NAVIGATION_CONTEXT_KEY: String = "Navigation"

/**
 * An [INavScopes] implementation that lets us coordinate updates to navigation state across
 * multiple `SentryNavEffect`s. (Also supports the trivial scenario where only a single
 * `SentryNavEffect` exists.)
 *
 * Needed because all `SentryNavEffect`s mutate a single [IScope] instance by default and will
 * clobber or interleave one another's updates unless managed properly:
 * ```
 * LifecycleOwner (e.g., Activity or Fragment)
 *       ↓ owns
 *  SentryNavEffect
 *       ↓
 *   CoordinatedBackStackObserver (wrapper)
 *     ├── BackStackObserver   — computes nav data and nav transactions
 *     └── NavScopes           — controls if nav data + txns reach the real scope
 *               ↓
 *        NavLeaseCoordinator — arbitrates ownership across all observers
 *               ↓
 *          shared IScope
 * ```
 *
 * **Limitations**
 *
 * Multi-effect support is restricted to the scenarios documented in the
 * [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/#supported-setups).
 *
 * This class implements the policy that underlies those scenarios. It assumes that, at any moment,
 * the user is interacting with at most one registered [LifecycleOwner] in the host app, and that
 * the `LifecycleOwner` being interacted with is the one that was most recently resumed.
 *
 * Concretely, a host app can safely use more than one `SentryNavEffect` only if:
 *
 * 1. each `SentryNavEffect` is called in the same composable as the `NavDisplay` it observes;
 * 2. no `SentryNavEffect` is called from inside a `NavDisplay`, e.g., from a nav entry, scene, or
 *    entry decorator†;
 * 3. each Activity or Fragment calls at most one `SentryNavEffect` from its own content (sibling
 *    Fragments may each have their own effect, but not alongside one in the Activity hosting them);
 *    and
 * 4. the user can interact with only one of those Activities or Fragments at a time.
 *
 * † Meant to ensure each effect's `LocalLifecycleOwner` is an Activity or a Fragment and not a
 * `NavDisplay`-provided owner. `NavDisplay` provides its own `LocalLifecycleOwner` to the entries
 * and scenes it renders. That owner drops to STARTED state during transitions and while overlays
 * are shown. An effect called inside a `NavDisplay` would therefore (wrongly) gain and lose the nav
 * lease this class manages as navigation animates, rather than as its Activity or Fragment comes
 * and goes.
 *
 * **A fundamental tradeoff**
 *
 * During `LifecycleOwner` overlap, the nav transaction and scoped nav data can reflect
 * **_either_**:
 *
 * 1. the outgoing owner that's still visible, or
 * 2. the incoming owner that may crash before it becomes visible,
 *
 * but not both. This class opts for (2) so that the incoming destination populates crash context
 * and any work performed is associated with it† – the idea being that start-up logic is more likely
 * to cause a crash or produce interesting spans than an outgoing destination's steady state.
 *
 * † Associating work with the incoming transaction means spans produced by the outgoing owner may
 * appear as if they were produced by the incoming owner (or vice versa, were our policy reversed).
 * A possible solution would be to implement a parent span delivery system similar to
 * `LocalSentrySpan`, but that's a task for another day.
 *
 * **Only leaseholders publish nav state or start nav transactions**
 *
 * This class works by only allowing `NavScopes` instances holding a lease to start nav transactions
 * and publish their navigation data to the shared `IScope`.
 *
 * `NavScopes` instances without a lease write to their own copy of [stagedNavData], which lets them
 * preserve their latest updates without overwriting another instance's published data. When an
 * instance acquires the lease, it publishes its staged data to the shared `IScope`.
 *
 * Likewise, a nav transaction requested without the lease is deferred: it starts when the instance
 * acquires the lease, provided no other transaction is bound to the shared `IScope` by then. See
 * [startTransaction].
 *
 * **Lease handoff policy**
 *
 * The lease is acquired preemptively by an incoming owner's `NavScopes` instance when it applies
 * its first [back stack update][onBackStackUpdate].†
 *
 * A displaced owner remains eligible for lease restoration until it [stops][ON_STOP]. It gets the
 * lease back when it [resumes][ON_RESUME], or sooner if the new owner leaves or pauses while the
 * displaced owner is still visible. If restored, the displaced owner's nav data and unfinished
 * transaction are reinstated.
 *
 * † There are some exceptions, e.g., the leaseholder is resumed and the incoming owner isn't (e.g.,
 * a preloaded ViewPager2 Fragment page next to the current one). Such an owner acquires the lease
 * when it resumes instead.
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
 *   ├────────────────────────► Navigation lease: A (no other resumed owner)
 *   │                          A remains eligible for restoration
 *   │
 *   │                                                                attach()
 *   │                                                                   │
 *   │                          Navigation lease: A ◄────────────────────┤
 *   │                          Scope transaction: txA                   │
 *   │                          Published data: A                        │
 *   │                          B registered, awaiting initial back      │
 *   │                            stack update                           │
 *   │                                                                   │
 *   │                                                   Initial back stack update
 *   │                                                                   │
 *   │                          Suspend & unbind txA ◄───────────────────┤
 *   │                          Navigation lease: B                      │
 *   │                          Published data: B                        │
 *   │                          Scope transaction: txB                   │
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
 *                              Navigation lease: B (no other resumed  ◄─┤
 *                                owner)                                 │
 *                                                                       │
 *                                                                   ON_STOP
 *                                                                       │
 *                              Navigation lease: B (no other resumed  ◄─┤
 *                                owner); B keeps recording navigation   │
 *                              B no longer eligible for restoration     │
 *                                                                       │
 *                                                                 ON_DESTROY
 *                                                                       │
 *                              Finish txB if unfinished; ◄──────────────┤
 *                                 unbind if still bound                 │
 *                              Clear B's remaining screen/back stack    │
 *                              Retain recorded breadcrumbs              │
 *                              Navigation lease: none                   │
 *                                                                       ▼
 *
 * Alternative at rollback point: B disposes before A stops
 *                              Finish & unbind txB ◄────── dispose B ───┤
 *                              Navigation lease: A                      │
 *                              Restore txA if unfinished and scope free │
 *                              Republish A's staged data                │
 * ```
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
) : INavScopes, LifecycleEventObserver {

  override val options: SentryOptions
    get() = delegate.options

  private val owner: Any = Any()
  private var attachedLifecycle: Lifecycle? = null
  private val stagedNavData = StagedNavData()

  /** The nav transaction most recently requested without the lease, if it's yet to start. */
  private var deferredTransaction: NavTransactionHandle? = null

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
      onPublishData = { scope ->
        stagedNavData.publishTo(scope)
        startDeferredTransaction(scope)
      },
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
   * Announces that a back stack update is about to be applied, so this instance can acquire the nav
   * lease if it's eligible to.
   *
   * Must be called before each update. If [configureScope][IScopes.configureScope] skips its
   * callback (e.g., the SDK is disabled), the update itself can't be applied either, so nothing is
   * recorded and the next update applied is treated as this instance's first.
   */
  fun onBackStackUpdate() {
    delegate.configureScope { scope -> coordinator.onBackStackUpdate(owner, scope) }
  }

  override fun configureScope(callback: (INavScope) -> Unit) {
    delegate.configureScope { scope ->
      callback(NavScope(scope, stagedNavData, coordinator, owner))
    }
  }

  /**
   * Starts a nav transaction if this instance holds the nav lease, or defers its start otherwise.
   *
   * Without the lease, returns a handle whose transaction starts when this instance acquires the
   * lease (e.g., a preloaded ViewPager2 page the user swipes to), provided the handle hasn't been
   * [finished][INavTransaction.finish] and no other transaction is bound to the scope by then. A
   * transaction started right away couldn't be bound to the scope, so its destination's spans would
   * attach to the leaseholder's bound transaction instead, and the empty transaction would be
   * dropped. Only the most recently requested transaction is deferred. The propagation context
   * rotation a new transaction implies is deferred until the lease is acquired, too, so the
   * destination gets a fresh trace even if its transaction never starts.
   *
   * The [coordinator] retains the leaseholder's transaction, so it can be restored if this instance
   * loses the lease and regains it while the transaction is still unfinished.
   */
  override fun startTransaction(
    transactionContext: TransactionContext,
    transactionOptions: TransactionOptions,
  ): INavTransaction? {
    if (!coordinator.hasLease(owner)) {
      stagedNavData.deferPropagationContextRotation()
      return NavTransactionHandle.deferred(transactionContext, transactionOptions).also {
        deferredTransaction = it
      }
    }

    val transaction =
      delegate.startTransaction(transactionContext, transactionOptions).takeUnless { it.isNoOp }
        ?: return null
    coordinator.retainTransaction(owner, transaction)
    return NavTransactionHandle.started(transaction)
  }

  /**
   * Starts the [deferredTransaction], if any, and binds it to [scope] unless another transaction is
   * bound to it.
   *
   * Called once this instance acquires the lease and publishes its staged data. A deferred
   * transaction exists only if this instance applied a back stack update without the lease, after
   * which it can only acquire the lease without a back stack update (see
   * [NavLeaseCoordinator.onBackStackUpdate]). So the start never races a back stack update that's
   * about to start a transaction of its own.
   */
  private fun startDeferredTransaction(scope: IScope) {
    val handle = deferredTransaction ?: return
    deferredTransaction = null
    if (!handle.isAwaitingStart) {
      return
    }

    // As when starting a nav transaction with the lease, a finished transaction doesn't block a new
    // one, but any other bound transaction (e.g., the host app's) does.
    scope.withTransaction { current ->
      if (current?.isFinished == true) {
        scope.clearTransaction()
      }
    }
    if (scope.transaction != null) {
      return
    }

    val transaction = handle.startDeferred(delegate) ?: return
    coordinator.retainTransaction(owner, transaction)
    scope.withTransaction { current ->
      if (current == null) {
        scope.transaction = transaction
      }
    }
  }

  /** Records [breadcrumb] immediately; breadcrumbs only append, so they aren't lease-protected. */
  override fun addBreadcrumb(breadcrumb: Breadcrumb, hint: Hint) {
    delegate.addBreadcrumb(breadcrumb, hint)
  }

  /**
   * Releases the lease, clears remaining published state, and finishes this instance's transaction.
   *
   * Also stops observing the attached [LifecycleOwner] and cleans up memory references.
   */
  fun dispose() {
    attachedLifecycle?.removeObserver(this)
    attachedLifecycle = null
    deferredTransaction = null

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
}

/**
 * The subset of the [IScopes] interface used to record navigation data.
 *
 * Protects against accidental misuse of what would otherwise be a partially-implemented `IScopes`
 * interface. New operations against the underlying `IScopes` instance must be added here.
 * Operations not directly delegable to `IScopes` should ***not*** be added.
 */
internal interface INavScopes {

  val options: SentryOptions

  /** Runs [callback] with the current [INavScope], if the SDK provides a scope. */
  fun configureScope(callback: (INavScope) -> Unit)

  /**
   * Starts a transaction, or returns `null` if none can be started (e.g., tracing is disabled, or
   * the transaction isn't sampled). The implementation may defer the start; see
   * [NavScopes.startTransaction].
   */
  fun startTransaction(
    transactionContext: TransactionContext,
    transactionOptions: TransactionOptions,
  ): INavTransaction?

  fun addBreadcrumb(breadcrumb: Breadcrumb, hint: Hint)
}
