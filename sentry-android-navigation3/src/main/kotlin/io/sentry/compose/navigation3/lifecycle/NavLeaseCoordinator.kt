package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
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
import io.sentry.SentryLevel

/**
 * Manages a lease in order to determine which [NavScopes] instance can bind nav transactions and
 * publish nav data to a shared [IScope].
 *
 * `NavScopes` instances register as [candidates][Candidate] for lease ownership via [register],
 * each on behalf of its [nav root][NavRootInfo]. Each candidate observes its nav root's lifecycle
 * until it's disposed.
 *
 * See the package README for the lease policy this class enforces.
 *
 * **Exception policy**
 *
 * Methods in this class with an `IScope` parameter can throw arbitrary exceptions, which propagate
 * to the caller. For instance, `IScope` can throw arbitrary exceptions when scope mutators notify
 * registered observers – including the host app – in an unguarded loop, or when starting a nav
 * transaction triggers the host app's callbacks.
 *
 * Each method updates its own bookkeeping so that the coordinator remains usable if the exception
 * is caught by the caller.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. All callers should confine their access to the same thread.
 */
@Suppress("TooManyFunctions")
internal class NavLeaseCoordinator {

  /** A list of all possible leaseholders. */
  private val candidates = mutableListOf<Candidate>()

  /** The actual leaseholder. */
  private var leaseHolder: Candidate? = null

  /** The last candidate to publish nav state, even if it no longer holds the lease. */
  private var lastPublisher: Candidate? = null

  /**
   * Counter that lets us order candidates by when this coordinator most recently observed them in
   * resumed state.
   */
  private var resumeOrder: Long = 0

  /**
   * Reports the host application's Activities to this coordinator so that it can detect whether a
   * leaseholder is covered by a non-candidate root.
   */
  private val activityWatcher = NavActivityWatcher(onActivityResumed = ::onAnyActivityResumed)

  /**
   * Registers a [nav root][rootInfo] as a lease candidate until disposal or lifecycle destruction.
   *
   * Returns `null` if the lifecycle is already destroyed.
   */
  fun register(
    scopes: IScopes,
    rootInfo: NavRootInfo,
    publisher: INavStatePublisher,
  ): NavLeaseRegistration? {
    val lifecycle = rootInfo.lifecycle
    val lifecycleState = lifecycle.currentState

    if (lifecycleState == Lifecycle.State.DESTROYED) {
      return null
    }

    rootInfo.activity?.application?.let { activityWatcher.watch(it) }

    val initialResumeOrder = if (lifecycleState == Lifecycle.State.RESUMED) ++resumeOrder else 0
    val candidate = Candidate(rootInfo, lifecycleState, initialResumeOrder, publisher, scopes)

    // Only list the candidate once addObserver() succeeds, so a failure can't leak the host
    // Activity.
    lifecycle.addObserver(candidate)
    candidates += candidate
    return candidate
  }

  /**
   * Gives [candidate] one chance to acquire the lease on its first back-stack update, and no-ops
   * otherwise.
   */
  private fun onBackStackUpdate(candidate: Candidate, scope: IScope) {
    if (candidate.hasHandledFirstUpdate) {
      return
    }

    candidate.hasHandledFirstUpdate = true

    val holder = leaseHolder

    // A nav root that isn't resumed while either the leaseholder or its own host Activity is
    // resumed isn't the one the user is interacting with (e.g., a preloaded ViewPager2 page next to
    // the current page, which may be a page without a SentryNavEffect and so never a leaseholder).
    // Instead, it acquires the lease when it resumes.
    val isBesideResumedContent =
      candidate.state != Lifecycle.State.RESUMED &&
        (holder?.state == Lifecycle.State.RESUMED || candidate.isHostActivityResumed)

    if (candidate.canAcquireLeaseOnFirstUpdate && !isBesideResumedContent) {
      transferLeaseTo(candidate, scope)
    }
  }

  /** Hands the lease to [next], or releases it if [scope] is unavailable. */
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
   * Releases the lease without handing it off and unpublishes its nav state from [scope]. The
   * candidate retains staged state for a later return. Without a scope, state remains published.
   *
   * Logs [withdrawalReason].
   */
  private fun withdrawLease(scope: IScope?, withdrawalReason: String) {
    val holder = leaseHolder ?: return

    leaseHolder = null
    scope
      ?.options
      ?.logger
      ?.log(
        SentryLevel.DEBUG,
        "Withdrew the Nav3 lease from a nav root hosted by %s: %s.",
        holder.hostActivity?.javaClass?.name,
        withdrawalReason,
      )
    if (scope != null && lastPublisher === holder) {
      // Clear the last publisher before calling into the scope (see the class's exception policy).
      lastPublisher = null
      holder.publisher.unpublishFrom(scope)
    }
  }

  /**
   * The eligible candidate most recently observed to be in a resumed state, other than [excluding].
   *
   * If [resumedOnly] is set to `true`, only candidates that are resumed right now are considered;
   * if `false`, started and paused candidates are also included.
   */
  private fun mostRecentlyResumedCandidate(
    excluding: Candidate? = null,
    resumedOnly: Boolean = false,
  ): Candidate? =
    candidates
      .filter {
        it !== excluding &&
          it.canReceiveLease &&
          it.state.isAtLeast(Lifecycle.State.STARTED) &&
          it.resumeOrder > 0 &&
          (!resumedOnly || it.state == Lifecycle.State.RESUMED)
      }
      .maxByOrNull { it.resumeOrder }

  /**
   * Records that [candidate] resumed.
   *
   * Gives [candidate] the lease if its back stack has had an initial update and [scope] is
   * available.
   */
  private fun resumeCandidate(candidate: Candidate, scope: IScope?) {
    // A null scope (e.g., the SDK is disabled) still records the lifecycle transition so that the
    // candidate remains eligible for fallback once the scope becomes available.
    candidate.resume(++resumeOrder)

    if (scope != null && candidate.canReceiveLease) {
      transferLeaseTo(candidate, scope)
    }
  }

  /**
   * Records that [candidate] paused.
   *
   * If [candidate] holds the lease when it pauses, hands it to another resumed nav root (e.g., a
   * Fragment whose dialog is being dismissed). If none exists, withdraws it when the host Activity
   * remains resumed; otherwise the paused nav root keeps the lease.
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
        candidate.isHostActivityResumed ->
          withdrawLease(
            scope,
            withdrawalReason = "the nav root paused while its host Activity stayed resumed",
          )
      }
    }
  }

  /**
   * Records that [candidate] stopped.
   *
   * If [candidate] holds the lease when it stops, hands it to another resumed nav root or keeps it
   * so a lone nav root can record navigation while the app is backgrounded.
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
   * Removes [candidate], unpublishes its state if needed, and hands off its lease to the latest
   * eligible candidate. Without [scope], releases the lease without unpublishing.
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
   * Whether [activity] owns a candidate's nav root that
   * [can receive the lease][Candidate.canReceiveLease].
   *
   * A returning Activity root takes the lease on resume and should not trigger withdrawal first.
   */
  private fun isActivityNavRoot(activity: Activity): Boolean = candidates.any {
    it.canReceiveLease && it.isActivityNavRoot(activity)
  }

  /** Withdraws the lease when the resumed [activity] appears to cover a non-resumed nav root. */
  private fun onAnyActivityResumed(activity: Activity) {
    // Assume the resumed `activity` is covering the current leaseholder if i) the leaseholder
    // itself isn't resumed, ii) `activity` isn't the leaseholder's host Activity, and iii)
    // `activity` isn't itself a nav root (see [isActivityNavRoot]). (i) checks the leaseholder's
    // own
    // state rather than its host Activity's, as they differ for Fragment nav roots: a Fragment
    // leaseholder that isn't resumed isn't what the user is interacting with, even if its host
    // Activity is resumed.
    val holder = leaseHolder ?: return
    val hostActivity = holder.hostActivity ?: return
    if (
      hostActivity === activity ||
        holder.state == Lifecycle.State.RESUMED ||
        isActivityNavRoot(activity)
    ) {
      return
    }

    holder.withScopeOrNull { scope ->
      withdrawLease(
        scope,
        withdrawalReason =
          "${activity.javaClass.name} was resumed while the nav root wasn't resumed",
      )
    }
  }

  /**
   * A [nav root][rootInfo] eligible to hold the lease managed by [NavLeaseCoordinator].
   *
   * [Disposed][dispose] upon the nav root's destruction.
   */
  private inner class Candidate(
    rootInfo: NavRootInfo,
    initialState: Lifecycle.State,
    initialResumeOrder: Long,
    val publisher: INavStatePublisher,
    private val scopes: IScopes,
  ) : NavLeaseRegistration, LifecycleEventObserver {

    val hostActivity: Activity? = rootInfo.activity

    /** Whether the host Activity is resumed; false when its lifecycle is unavailable. */
    val isHostActivityResumed: Boolean
      get() = (hostActivity as? LifecycleOwner)?.lifecycle?.currentState == Lifecycle.State.RESUMED

    /** Most recent resume order; zero until this candidate first resumes. */
    var resumeOrder: Long = initialResumeOrder
      private set

    /** The candidate's last observed lifecycle state. */
    var state: Lifecycle.State = initialState
      private set

    /** Whether this candidate has handled its first back-stack update. */
    var hasHandledFirstUpdate: Boolean = false

    /**
     * Whether the first back-stack update may take the lease: the nav root is resumed or has not
     * yet resumed or stopped since registration.
     */
    val canAcquireLeaseOnFirstUpdate: Boolean
      get() = state == Lifecycle.State.RESUMED || (resumeOrder == 0L && !wasStopped)

    /**
     * Whether this candidate can acquire the lease on a lifecycle change, i.e., when it resumes or
     * as a fallback.
     *
     * False until its first back stack update is handled, as it has no nav state to publish before
     * then.
     */
    val canReceiveLease: Boolean
      get() = hasHandledFirstUpdate

    override val hasLease: Boolean
      get() = leaseHolder === this

    private val lifecycle: Lifecycle = rootInfo.lifecycle

    /** Whether this candidate has stopped since registration. */
    private var wasStopped: Boolean = false

    private var isDisposed = false

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
     * Runs [block] with the scope, or with `null` if [IScopes.configureScope] skips its callback.
     * Does not rerun a callback that throws.
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

    /** Whether [activity] itself owns this nav root, rather than a Fragment it hosts. */
    fun isActivityNavRoot(activity: Activity): Boolean =
      hostActivity === activity && (activity as? LifecycleOwner)?.lifecycle === lifecycle
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

  /** Gives the candidate a chance to acquire the lease before its first back-stack update. */
  fun onBackStackUpdate()

  /**
   * Unregisters the candidate, releases its lease, and unpublishes its nav state. Calls
   * [INavStatePublisher.onUnregistered] even if cleanup throws. Repeated calls do nothing.
   */
  fun dispose()
}

/** Publishes a [NavLeaseCoordinator.Candidate]'s nav state to the shared [IScope]. */
internal interface INavStatePublisher {

  /** Publishes the candidate's staged nav state to [scope]. */
  fun publishTo(scope: IScope)

  /** Removes published nav state from [scope], retaining it for a later lease acquisition. */
  fun unpublishFrom(scope: IScope)

  /** Called after unregistering, with the affected [scope] or `null` if none was available. */
  fun onUnregistered(scope: IScope?)
}
