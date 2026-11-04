package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
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
 * `NavScopes` instances register as [candidates][Candidate] for lease ownership via [register],
 * each on behalf of its effect's [nav root][NavRoot]. Each candidate observes its nav root's
 * lifecycle until it's disposed.
 *
 * **A fundamental tradeoff**
 *
 * While nav roots overlap (e.g., during an Activity transition), the nav transaction and scoped nav
 * data can reflect **_either_**:
 *
 * 1. the outgoing nav root that's still visible, or
 * 2. the incoming nav root that may crash before it becomes visible,
 *
 * but not both. This class opts for (2) so that the incoming destination populates crash context
 * and any work performed is associated with it† – the idea being that start-up logic is more likely
 * to cause a crash or produce interesting spans than an outgoing destination's steady state.
 *
 * † Associating work with the incoming transaction means spans produced by the outgoing nav root
 * may appear as if they were produced by the incoming nav root (or vice versa, were our policy
 * reversed).
 *
 * **Lease handoff policy**
 *
 * The lease is acquired preemptively by an incoming nav root's `NavScopes` instance when it applies
 * its first [back stack update][onBackStackUpdate].†
 *
 * A displaced nav root remains eligible for lease restoration until it
 * [stops][Lifecycle.Event.ON_STOP]. It gets the lease back when it
 * [resumes][Lifecycle.Event.ON_RESUME], or sooner if the new nav root leaves or pauses while the
 * displaced nav root is still visible. If restored, the displaced nav root's nav data and
 * unfinished transaction are reinstated.
 *
 * † There are some exceptions, e.g., the leaseholder is resumed and the incoming nav root isn't
 * (e.g., a preloaded ViewPager2 Fragment page next to the current one). Such a nav root acquires
 * the lease when it resumes instead.
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
 *   ├────────────────────────► Navigation lease: A (no other resumed nav root)
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
 *                                nav root)                              │
 *                                                                       │
 *                                                                   ON_STOP
 *                                                                       │
 *                              Navigation lease: B (no other resumed  ◄─┤
 *                                nav root); B keeps recording           │
 *                                navigation                             │
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
 * **Lease withdrawal**
 *
 * An Activity or Fragment without a `SentryNavEffect` (aka, a "non-candidate root") can't take the
 * lease, so when one replaces the leaseholder, the coordinator **withdraws** the lease instead,
 * i.e., it unpublishes the leaseholder's nav state without handing off the lease, leaving the
 * transaction unbound but running until the leaseholder resumes.
 *
 * Non-candidate roots never register, so the coordinator infers their presence from either of two
 * signals:
 *
 * - another Activity is created or resumed while the leaseholder isn't resumed; or
 * - the leaseholder pauses while its host Activity stays resumed (e.g., a ViewPager2 swipe). (Note
 *   that an Activity pauses before its Fragments when the Activity is backgrounded or covered.)
 *
 * Limitations: Leaseholders whose host Activity is unknown are never withdrawn. Fragments that
 * cover the leaseholder without pausing it (e.g., dialogs) go undetected.
 *
 * **Returning to a nav root doesn't start a new transaction**
 *
 * Under the current implementation, only initialization of the host app's back stack or updates to
 * it trigger nav transactions. Returning to a nav root in situations where the nav root was not
 * destroyed and recreated in the meantime (e.g., swiping back to a nearby ViewPager2 page) re-binds
 * the old nav root's transaction but doesn't create a new one. See
 * [#6454](https://github.com/getsentry/sentry-java/issues/6454) for more details.
 *
 * **Lifetime**
 *
 * The default coordinator lives as long as the process, since lease state follows nav root
 * lifecycles rather than the SDK's. Closing and re-initializing the SDK doesn't call for a reset:
 *
 * - `SentryNavEffect` resolves the current scopes on every call;
 * - while the SDK is closed, bookkeeping continues without a scope; and
 * - unpublishing compares by reference, so values published to an earlier scope don't affect the
 *   current one.
 *
 * Limitation: A nav transaction that's still running when the SDK is closed and re-initialized can
 * be bound to the new scope the next time it's published, although its client has been closed.
 * Spans then attach to it, and are lost, until it times out or the next navigation finishes it.
 *
 * **Exception policy**
 *
 * Methods in this class with an `IScope` parameter can throw arbitrary exceptions†, which propagate
 * to the caller. Each method updates its own bookkeeping before calling into the scope or a
 * candidate's [INavStatePublisher], so the coordinator remains usable if the exception is caught.
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
  private var lastPublisher: Candidate? = null

  private var leaseHolder: Candidate? = null

  /**
   * Counter that orders candidates by when this coordinator most recently observed them in resumed
   * state.
   */
  private var resumeOrder: Long = 0

  /**
   * Whether this coordinator observes the host application's Activity lifecycles in order to detect
   * a leaseholder covered by a non-candidate root.
   *
   * The [ActivityLifecycleCallbacks] are registered once per coordinator and never unregistered.
   * Like the default coordinator, they're meant to live as long as the process.
   */
  private var isWatchingActivities = false

  /**
   * Registers a candidate for the nav lease on behalf of its nav [root]. The candidate observes the
   * nav root's [lifecycle][NavRoot.lifecycle] until it's [disposed][NavLeaseRegistration.dispose]
   * or that lifecycle is destroyed.
   *
   * Returns `null` if the lifecycle is already [destroyed][Lifecycle.State.DESTROYED].
   */
  fun register(
    scopes: IScopes,
    root: NavRoot,
    publisher: INavStatePublisher,
  ): NavLeaseRegistration? {
    val lifecycle = root.lifecycle
    val lifecycleState = lifecycle.currentState

    if (lifecycleState == Lifecycle.State.DESTROYED) {
      return null
    }

    val hostActivity = root.activity
    hostActivity?.application?.let { watchActivitiesOf(it) }

    val initialResumeOrder = if (lifecycleState == Lifecycle.State.RESUMED) ++resumeOrder else 0
    val candidate =
      Candidate(scopes, lifecycle, hostActivity, publisher, lifecycleState, initialResumeOrder)
    candidates += candidate

    lifecycle.addObserver(candidate)
    return candidate
  }

  private fun watchActivitiesOf(application: Application) {
    if (isWatchingActivities) {
      return
    }
    isWatchingActivities = true
    application.registerActivityLifecycleCallbacks(CoveringActivityCallbacks())
  }

  /**
   * Callback to be invoked when any Activity in the application is created or resumed.
   *
   * Withdraws the lease from [the current leaseholder][leaseHolder] if the created or resumed
   * [activity] appears to be covering it.
   */
  private fun onActivityCreatedOrResumed(activity: Activity) {
    // Assume the created or resumed `activity` is covering the current leaseholder's Activity if
    // i) the leaseholder's Activity isn't resumed, and ii) the created or resumed `activity` != the
    // leaseholder's Activity.
    val holder = leaseHolder ?: return
    val hostActivity = holder.hostActivity ?: return
    if (hostActivity === activity || holder.state == Lifecycle.State.RESUMED) {
      return
    }

    holder.withScopeOrNull { scope -> withdrawLease(scope) }
  }

  /**
   * Releases the [current leaseholder's][leaseHolder] lease without handing it to another
   * candidate, and unpublishes the leaseholder's nav state from [scope]. The candidate keeps its
   * staged nav state, so it can publish it again if it re-acquires the lease.
   *
   * Without a scope (e.g., the SDK is disabled), the published nav state is left in place, and the
   * next handoff unpublishes it.
   */
  private fun withdrawLease(scope: IScope?) {
    val holder = leaseHolder ?: return

    leaseHolder = null
    if (scope != null && lastPublisher === holder) {
      // Clear the last publisher before calling into the scope (see the class's exception policy).
      lastPublisher = null
      holder.publisher.unpublishFrom(scope)
    }
  }

  /**
   * Records that [candidate] is about to apply a back-stack update and acquires the nav lease if
   * the nav root's lifecycle permits it.
   *
   * Later calls for the same [candidate] do nothing.
   */
  private fun onBackStackUpdate(candidate: Candidate, scope: IScope) {
    if (candidate.hasAppliedInitialBackStackUpdate) {
      return
    }

    candidate.hasAppliedInitialBackStackUpdate = true

    val holder = leaseHolder

    // A nav root that isn't resumed doesn't take the lease from a resumed leaseholder, as it isn't
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
   * Hands the lease to another nav root if that nav root is resumed, e.g., a Fragment whose dialog
   * is being dismissed. Otherwise, withdraws the lease if [candidate] paused within its resumed
   * host Activity, and keeps it if not. (See "Lease Withdrawal" in the class KDoc.)
   */
  private fun pauseCandidate(candidate: Candidate, scope: IScope?) {
    candidate.pause()

    // A paused leaseholder may still be the topmost visible nav root (e.g., beneath a translucent
    // window or picture-in-picture mode), and any other paused nav root is beneath it. A displaced
    // nav root will acquire the lease again when it resumes.
    if (leaseHolder === candidate) {
      val next = mostRecentlyResumedCandidate(excluding = candidate, resumedOnly = true)
      when {
        next != null -> handOffLease(next, scope)
        candidate.isHostActivityResumed -> withdrawLease(scope)
      }
    }
  }

  /**
   * Records that [candidate] stopped.
   *
   * As with pausing, hands the lease to another nav root only if that nav root is resumed;
   * otherwise the stopped nav root keeps it. Keeping it lets a lone nav root go on recording
   * navigation that happens while it isn't resumed (e.g., while the app is in the background, or
   * after it restarts but before it resumes), including starting nav transactions.
   */
  private fun stopCandidate(candidate: Candidate, scope: IScope?) {
    candidate.stop()

    // A paused nav root doesn't get the lease. When the leaseholder stops before it, the app is
    // typically going to the background (e.g., a translucent leaseholder stops before the nav root
    // beneath it), and that nav root isn't what the user last saw. A paused nav root that comes
    // back to the foreground will acquire the lease when it resumes.
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

    val previous = lastPublisher
    leaseHolder = candidate
    if (candidate != null) {
      // Record the candidate as the last publisher before calling into the scope: it owns the
      // scope's nav state from the moment it takes the lease. If unpublishing the previous nav
      // root's state then throws, the next handoff still unpublishes the candidate's state rather
      // than never unpublishing it.
      lastPublisher = candidate
      if (previous != null && previous !== candidate) {
        previous.publisher.unpublishFrom(scope)
      }
      candidate.publisher.publishTo(scope)
    }
  }

  /**
   * Stops tracking [candidate] (e.g., because it was destroyed).
   *
   * Unpublishes `candidate`'s nav state from [scope] if it's the most recently published. If
   * `candidate` holds the lease, hands it to the eligible candidate most recently observed resumed,
   * even if that candidate is now only started or paused, or releases it if there's none.
   */
  private fun unregisterCandidate(candidate: Candidate, scope: IScope?) {
    val wasLastPublisher = lastPublisher === candidate
    val wasLeaseHolder = leaseHolder === candidate

    // Remove candidate before any scope calls to ensure it's released even if cleanup or handoff
    // throws.
    candidates.remove(candidate)

    if (wasLastPublisher) {
      lastPublisher = null
    }
    if (wasLeaseHolder) {
      leaseHolder = null
    }

    // A null scope (e.g., the SDK is disabled) still removes the candidate, but its published nav
    // state is left in place and the lease is released rather than handed off.
    if (scope == null) {
      return
    }

    if (wasLastPublisher) {
      candidate.publisher.unpublishFrom(scope)
    }
    if (wasLeaseHolder) {
      transferLeaseTo(mostRecentlyResumedCandidate(), scope)
    }
  }

  /**
   * A nav root's registration as a candidate for lease ownership. Relays the nav root's lifecycle
   * events to the coordinator until it's [disposed][dispose].
   */
  private inner class Candidate(
    private val scopes: IScopes,
    private val lifecycle: Lifecycle,
    /** The Activity that displays the candidate's content, if any. */
    val hostActivity: Activity?,
    val publisher: INavStatePublisher,
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
     * [registration][NavLeaseCoordinator.register] to acquire the nav lease: it's resumed, or it
     * has neither resumed nor stopped since registration (i.e., it's still coming up for the first
     * time).
     */
    val canAcquireLeaseOnFirstUpdate: Boolean
      get() = state == Lifecycle.State.RESUMED || (resumeOrder == 0L && !wasStopped)

    /**
     * Whether the candidate's [host Activity][hostActivity] is resumed right now. `false` if the
     * host Activity is unknown or isn't a [LifecycleOwner], since its state can't be read then.
     */
    val isHostActivityResumed: Boolean
      get() = (hostActivity as? LifecycleOwner)?.lifecycle?.currentState == Lifecycle.State.RESUMED

    override val hasLease: Boolean
      get() = leaseHolder === this

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
          publisher.onUnregistered(scope)
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
    fun withScopeOrNull(block: (IScope?) -> Unit) {
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

  /**
   * Reports each Activity that's created or resumed to [onActivityCreatedOrResumed], so that a
   * leaseholder the Activity covers can be withdrawn.
   *
   * Reports an Activity when it's created, so that withdrawal can free the scope's transaction slot
   * before `ActivityLifecycleIntegration` binds the Activity's `ui.load` transaction, and again
   * when it resumes, for an existing Activity brought back to the front. Reporting the same
   * Activity more than once is harmless, as there's no leaseholder left to withdraw.
   */
  private inner class CoveringActivityCallbacks : ActivityLifecycleCallbacks {

    // Only called on API 29+, before any callback's onActivityCreated.
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
      onActivityCreatedOrResumed(activity)
    }

    // Covers API levels below 29, which don't call onActivityPreCreated. Runs too late to free the
    // transaction slot for ui.load: ActivityLifecycleIntegration's onActivityCreated, registered
    // earlier, finds the slot taken and leaves ui.load unbound. Spans that other instrumentation
    // creates while the Activity loads are then attached to no transaction rather than to ui.load.
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
      onActivityCreatedOrResumed(activity)
    }

    override fun onActivityResumed(activity: Activity) {
      onActivityCreatedOrResumed(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
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
   * lease if it's eligible to. Does nothing once the candidate is unregistered.
   */
  fun onBackStackUpdate()

  /**
   * Stops observing the candidate's lifecycle and unregisters it: releases the lease, unpublishes
   * the candidate's nav state, and then calls [INavStatePublisher.onUnregistered], even if
   * unregistering throws. Does nothing once the candidate is unregistered.
   */
  fun dispose()
}

/** Publishes a [NavLeaseCoordinator.Candidate]'s nav state to the shared [IScope]. */
internal interface INavStatePublisher {

  /** Publishes the candidate's staged nav state to [scope]. */
  fun publishTo(scope: IScope)

  /**
   * Removes the candidate's published nav state from [scope].
   *
   * Unpublished nav state remains staged so that it can be re-published if the candidate regains
   * the lease.
   */
  fun unpublishFrom(scope: IScope)

  /**
   * Called when the lease candidate is unregistered, after its nav state has been unpublished.
   *
   * The provided [scope] is the scope the candidate was unregistered against, or `null` if there
   * was none (e.g., the SDK is disabled).
   */
  fun onUnregistered(scope: IScope?)
}
