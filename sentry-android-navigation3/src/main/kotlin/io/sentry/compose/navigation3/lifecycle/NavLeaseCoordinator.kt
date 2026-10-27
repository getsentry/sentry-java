package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_PAUSE
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.sentry.IScope
import io.sentry.IScopes

/**
 * Manages a lease in order to determine which [NavScopes] instance can bind nav transactions and
 * publish nav data to a shared [IScope].
 *
 * `NavScopes` instances register as [candidates][Candidate] for lease ownership via [register].
 * Each candidate observes its owner's lifecycle until it's disposed.
 *
 * Note: "Owner" below refers to the `LifecycleOwner` whose lifecycle the candidate observes.
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
 * **Lease handoff policy**
 *
 * The lease is acquired preemptively by an incoming owner's `NavScopes` instance when it applies
 * its first [back stack update][onBackStackUpdate].†
 *
 * A displaced owner remains eligible for lease restoration until it
 * [stops][Lifecycle.Event.ON_STOP]. It gets the lease back when it
 * [resumes][Lifecycle.Event.ON_RESUME], or sooner if the new owner leaves or pauses while the
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
 * NavScopes A + Lifecycle A     Shared state            NavScopes B + Lifecycle B
 * ─────────────────────────     ────────────            ─────────────────────────
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
 *                              Retain recorded nav events               │
 *                              Navigation lease: none                   │
 *                                                                       ▼
 *
 * Alternative at rollback point: B disposes before A stops
 *                              Unbind txB ◄─────────────── dispose B ───┤
 *                              Navigation lease: A                      │
 *                              Republish A's staged data                │
 *                              Restore txA if unfinished and scope free │
 *                              Finish txB if unfinished                 │
 * ```
 *
 * **Exception policy**
 *
 * Methods in this class with an `IScope` parameter can throw arbitrary exceptions†, which propagate
 * to the caller. Each method updates its own bookkeeping before calling into the scope or a
 * candidate's [LeasedNavState], so the coordinator remains usable if the exception is caught.
 * Candidates call these methods from [configureScope][IScopes.configureScope] callbacks, whose
 * error handling catches the exceptions.
 *
 * † E.g., `IScope` can throw arbitrary exceptions when scope mutators notify registered observers –
 * including the host app – in an unguarded loop, or when starting a nav transaction triggers the
 * host app's callbacks.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. All `NavScopes` instances using this coordinator must
 * confine their access to the same thread.
 */
@Suppress("TooManyFunctions")
internal class NavLeaseCoordinator {

  private val candidates = mutableListOf<Candidate>()

  /** Candidate whose nav state was most recently published to the shared scope. */
  private var publisher: Candidate? = null

  private var leaseHolder: Candidate? = null

  /**
   * Counter that orders candidates by when this coordinator most recently observed them in resumed
   * state.
   */
  private var resumeOrder: Long = 0

  /**
   * Registers a candidate for the nav lease that observes [lifecycle] until it's
   * [disposed][NavLeaseRegistration.dispose] or `lifecycle` is destroyed. Returns `null` if
   * `lifecycle` is already [destroyed][Lifecycle.State.DESTROYED].
   */
  fun register(
    lifecycle: Lifecycle,
    scopes: IScopes,
    navState: LeasedNavState,
  ): NavLeaseRegistration? {
    val lifecycleState = lifecycle.currentState
    if (lifecycleState == Lifecycle.State.DESTROYED) {
      return null
    }

    val initialResumeOrder = if (lifecycleState == Lifecycle.State.RESUMED) ++resumeOrder else 0
    val candidate = Candidate(lifecycle, scopes, navState, lifecycleState, initialResumeOrder)
    candidates += candidate

    lifecycle.addObserver(candidate)
    return candidate
  }

  /**
   * Records that [candidate] is about to apply a back-stack update and acquires the nav lease if
   * the owner's lifecycle permits it.
   *
   * Later calls for the same [candidate] do nothing.
   *
   * Can throw arbitrary exceptions. See "Exception policy" in the class KDoc.
   */
  private fun onBackStackUpdate(candidate: Candidate, scope: IScope) {
    if (candidate.hasAppliedInitialBackStackUpdate) {
      return
    }

    candidate.hasAppliedInitialBackStackUpdate = true

    val holder = leaseHolder

    // An owner that isn't resumed doesn't take the lease from a resumed leaseholder, as it isn't
    // the one the user is interacting with (e.g., a preloaded ViewPager2 page next to the current
    // page). Instead, it acquires the lease when it resumes.
    val wouldDisplaceResumedHolder =
      holder != null &&
        holder.state == Lifecycle.State.RESUMED &&
        candidate.state != Lifecycle.State.RESUMED

    if (candidate.canAcquireLeaseOnFirstUpdate && !wouldDisplaceResumedHolder) {
      transferLeaseTo(candidate, scope)
    }
  }

  /**
   * Records that [candidate] resumed.
   *
   * Restores the lease to `candidate` if it already applied a back stack update and [scope] is
   * non-null.
   *
   * Can throw arbitrary exceptions. See "Exception policy" in the class KDoc.
   */
  private fun resumeCandidate(candidate: Candidate, scope: IScope?) {
    // A null scope (e.g., the SDK is disabled) still records the lifecycle transition so that the
    // candidate remains eligible for fallback once the scope becomes available.
    candidate.resume(++resumeOrder)

    if (scope != null && candidate.hasAppliedInitialBackStackUpdate) {
      transferLeaseTo(candidate, scope)
    }
  }

  /**
   * Records that [candidate] paused.
   *
   * Hands the lease to another owner only if that owner is resumed (e.g., a Fragment whose dialog
   * is being dismissed); otherwise keeps it.
   *
   * Can throw arbitrary exceptions. See "Exception policy" in the class KDoc.
   */
  private fun pauseCandidate(candidate: Candidate, scope: IScope?) {
    candidate.pause()

    // A paused leaseholder may still be the topmost visible owner (e.g., beneath a translucent
    // window or picture-in-picture mode), and any other paused owner is beneath it. A displaced
    // owner will acquire the lease again when it resumes.
    if (leaseHolder === candidate) {
      mostRecentlyResumedCandidate(excluding = candidate, resumedOnly = true)?.let { next ->
        handOffLease(next, scope)
      }
    }
  }

  /**
   * Records that [candidate] stopped.
   *
   * As with pausing, hands the lease to another owner only if that owner is resumed; otherwise the
   * stopped owner keeps it. Keeping it lets a lone owner go on recording navigation that happens
   * while it isn't resumed (e.g., while the app is in the background, or after it restarts but
   * before it resumes), including starting nav transactions.
   *
   * Can throw arbitrary exceptions. See "Exception policy" in the class KDoc.
   */
  private fun stopCandidate(candidate: Candidate, scope: IScope?) {
    candidate.stop()

    // A paused owner doesn't get the lease. When the leaseholder stops before it, the app is
    // typically going to the background (e.g., a translucent leaseholder stops before the owner
    // beneath it), and that owner isn't what the user last saw. A paused owner that comes back to
    // the foreground will acquire the lease when it resumes.
    if (leaseHolder === candidate) {
      mostRecentlyResumedCandidate(resumedOnly = true)?.let { next -> handOffLease(next, scope) }
    }
  }

  /**
   * The eligible candidate most recently observed to be in a resumed state, other than [excluding].
   *
   * If [resumedOnly] is set to `true`, only candidates that are resumed right now are considered;
   * if `false`, started and paused candidates are also considered.
   */
  private fun mostRecentlyResumedCandidate(
    excluding: Candidate? = null,
    resumedOnly: Boolean = false,
  ): Candidate? =
    candidates
      .filter {
        it !== excluding &&
          it.hasAppliedInitialBackStackUpdate &&
          it.state.isAtLeast(Lifecycle.State.STARTED) &&
          it.resumeOrder > 0 &&
          (!resumedOnly || it.state == Lifecycle.State.RESUMED)
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

  /** Hands the lease to [candidate], or releases it given `null`. */
  private fun transferLeaseTo(candidate: Candidate?, scope: IScope) {
    if (leaseHolder === candidate) {
      return
    }

    val previousPublisher = publisher
    leaseHolder = candidate
    if (candidate != null) {
      // Record the candidate as the publisher before calling into the scope: it owns the scope's
      // nav state from the moment it takes the lease. If unpublishing the previous owner's state
      // then throws, the next handoff still unpublishes the candidate's state rather than never
      // unpublishing it.
      publisher = candidate
      if (previousPublisher != null && previousPublisher !== candidate) {
        previousPublisher.navState.unpublishFrom(scope)
      }
      candidate.navState.publishTo(scope)
    }
  }

  /**
   * Stops tracking [candidate] (e.g., because it was destroyed).
   *
   * Unpublishes `candidate`'s nav state from [scope] if it's the most recently published. If
   * `candidate` holds the lease, hands it to the eligible candidate most recently observed resumed,
   * even if that candidate is now only started or paused, or releases it if there's none.
   *
   * Can throw arbitrary exceptions. See "Exception policy" in the class KDoc.
   */
  private fun unregisterCandidate(candidate: Candidate, scope: IScope?) {
    val wasPublisher = publisher === candidate
    val wasLeaseHolder = leaseHolder === candidate

    // Remove candidate before any scope calls to ensure it's released even if cleanup or handoff
    // throws.
    candidates.remove(candidate)

    if (wasPublisher) {
      publisher = null
    }
    if (wasLeaseHolder) {
      leaseHolder = null
    }

    // A null scope (e.g., the SDK is disabled) still removes the candidate, but its published nav
    // state is left in place and the lease is released rather than handed off.
    if (scope == null) {
      return
    }

    if (wasPublisher) {
      candidate.navState.unpublishFrom(scope)
    }
    if (wasLeaseHolder) {
      transferLeaseTo(mostRecentlyResumedCandidate(), scope)
    }
  }

  /**
   * A [LifecycleOwner] registered as a candidate for lease ownership. Relays its owner's lifecycle
   * events to the coordinator until it's [disposed][dispose].
   */
  private inner class Candidate(
    private val lifecycle: Lifecycle,
    private val scopes: IScopes,
    val navState: LeasedNavState,
    initialState: Lifecycle.State,
    initialResumeOrder: Long,
  ) : NavLeaseRegistration, LifecycleEventObserver {

    /** The candidate's lifecycle state, as last observed. */
    var state: Lifecycle.State = initialState
      private set

    /**
     * Whether the candidate has been observed STOPPED since
     * [registration][NavLeaseCoordinator.register].
     */
    private var wasStopped: Boolean = false

    /**
     * See [NavLeaseCoordinator.resumeOrder]. Non-zero once the candidate has been observed RESUMED
     * since [registration][NavLeaseCoordinator.register].
     */
    var resumeOrder: Long = initialResumeOrder
      private set

    var hasAppliedInitialBackStackUpdate: Boolean = false

    /**
     * Whether the candidate's lifecycle permits its first back stack update since
     * [registration][NavLeaseCoordinator.register] to acquire the nav lease, including taking it
     * from another owner that isn't resumed: it's resumed, or it has neither resumed nor stopped
     * since registration (i.e., it's still coming up for the first time). A paused or restarted
     * candidate is returning to the screen rather than arriving on it.
     */
    val canAcquireLeaseOnFirstUpdate: Boolean
      get() = state == Lifecycle.State.RESUMED || (resumeOrder == 0L && !wasStopped)

    fun start() {
      // Attaching to an already-resumed lifecycle replays ON_START before ON_RESUME, so only move
      // up from CREATED.
      if (!state.isAtLeast(Lifecycle.State.STARTED)) {
        state = Lifecycle.State.STARTED
      }
    }

    fun resume(order: Long) {
      resumeOrder = order
      state = Lifecycle.State.RESUMED
    }

    fun pause() {
      state = Lifecycle.State.STARTED
    }

    fun stop() {
      state = Lifecycle.State.CREATED
      wasStopped = true
    }

    private var isDisposed = false

    override val hasLease: Boolean
      get() = leaseHolder === this

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
      when (event) {
        ON_START -> start()
        ON_RESUME -> withScopeOrNull { resumeCandidate(this, it) }
        ON_PAUSE -> withScopeOrNull { pauseCandidate(this, it) }
        ON_STOP -> withScopeOrNull { stopCandidate(this, it) }
        ON_DESTROY -> dispose()
        else -> Unit
      }
    }

    override fun onBackStackUpdate() {
      if (isDisposed) {
        return
      }
      scopes.configureScope { scope -> this@NavLeaseCoordinator.onBackStackUpdate(this, scope) }
    }

    override fun dispose() {
      if (isDisposed) {
        return
      }
      isDisposed = true
      lifecycle.removeObserver(this)

      withScopeOrNull { scope ->
        try {
          unregisterCandidate(this, scope)
        } finally {
          navState.onUnregistered(scope)
        }
      }
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
      scopes.configureScope { scope ->
        callbackInvoked = true
        block(scope)
      }

      if (!callbackInvoked) {
        block(null)
      }
    }
  }
}

/**
 * A candidate's registration with a [NavLeaseCoordinator], starting from
 * [NavLeaseCoordinator.register] and continuing until it's [disposed][dispose] or its lifecycle is
 * destroyed.
 */
internal interface NavLeaseRegistration {

  /** Whether the candidate holds the nav lease. */
  val hasLease: Boolean

  /**
   * Announces that the candidate is about to apply a back stack update, so it can acquire the nav
   * lease if it's eligible to. Does nothing once the candidate is unregistered. See
   * [NavScopes.onBackStackUpdate].
   */
  fun onBackStackUpdate()

  /**
   * Stops observing the candidate's lifecycle and unregisters it: releases the lease, unpublishes
   * the candidate's nav state, and then calls [LeasedNavState.onUnregistered], even if
   * unregistering throws. Does nothing once the candidate is unregistered.
   */
  fun dispose()
}

/**
 * The nav state a [NavLeaseCoordinator] candidate publishes to the shared [IScope] while it holds
 * the nav lease.
 *
 * Nav transactions and nav data each occupy a single, mutually-exclusive slot in the `IScope`, so
 * only one candidate's nav state can be published at a time. Unpublishing nav state removes it from
 * the scope but keeps it staged, so the candidate can publish it again if it regains the lease.
 */
internal interface LeasedNavState {

  /** Publishes staged nav state to [scope]. */
  fun publishTo(scope: IScope)

  /** Removes published nav state from [scope], keeping it staged so it can be republished. */
  fun unpublishFrom(scope: IScope)

  /**
   * Called when the lease candidate is unregistered, after its nav state has been unpublished.
   *
   * The provided [scope] is the scope the candidate was unregistered against, or `null` if there
   * was none (e.g., the SDK is disabled).
   */
  fun onUnregistered(scope: IScope?)
}
