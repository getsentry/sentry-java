package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.sentry.IScope
import io.sentry.ITransaction
import io.sentry.SpanStatus

/**
 * Manages a lease in order to determine which [NavScopes] instance can bind nav transactions and
 * publish nav data to a shared [IScope].
 *
 * `NavScopes` instances register as [candidates][Candidate] for lease ownership via
 * [registerCandidate].
 *
 * Note: "Owner" below refers to the `LifecycleOwner` whose lifecycle events the candidate's
 * `NavScopes` relays.
 *
 * **Exception semantics**
 *
 * Methods in this class with an `IScope` parameter can throw arbitrary exceptions†, which propagate
 * to the caller. This class restores its own bookkeeping first, so the coordinator remains usable
 * if the exception is caught.
 *
 * † E.g., `IScope` can throw arbitrary exceptions when scope mutators notify registered observers –
 * including the host app – in an unguarded loop, or when `finish()` is called on a retained nav
 * transaction, which can trigger the host app's event processors.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. All `NavScopes` instances using this coordinator must
 * confine their access to the same thread.
 */
@Suppress("TooManyFunctions")
internal class NavLeaseCoordinator {

  private val candidates = mutableListOf<Candidate>()

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

  /**
   * Registers [owner] as a candidate for the nav lease. No-ops if `owner` instance is already
   * registered or [lifecycleState] is [destroyed][Lifecycle.State.DESTROYED].
   *
   * [onPublishData] publishes `owner`'s staged nav data to the given scope when `owner` acquires
   * the lease. [onClearPublishedData] clears `owner`'s published nav data from the given scope when
   * its data is the most recently published and either another owner acquires the lease or `owner`
   * is unregistered.
   */
  fun registerCandidate(
    owner: Any,
    lifecycleState: Lifecycle.State,
    onPublishData: (IScope) -> Unit,
    onClearPublishedData: (IScope) -> Unit,
  ) {
    if (findCandidate(owner) != null) {
      return
    }

    if (lifecycleState == Lifecycle.State.DESTROYED) {
      return
    }

    val initialResumeOrder = if (lifecycleState == Lifecycle.State.RESUMED) ++resumeOrder else 0
    candidates +=
      Candidate(owner, onPublishData, onClearPublishedData, lifecycleState, initialResumeOrder)
  }

  /** Records that [owner] started. */
  fun startCandidate(owner: Any) {
    findCandidate(owner)?.start()
  }

  /**
   * Records that [owner] resumed.
   *
   * Restores the lease to `owner` if it already applied a back stack update and [scope] is
   * non-null.
   *
   * A `null` scope (e.g., the SDK is disabled) still records the lifecycle transition so the
   * candidate remains eligible for fallback once a scope becomes available.
   *
   * Can throw arbitrary exceptions. See "Exception semantics" in the class KDoc.
   */
  fun resumeCandidate(owner: Any, scope: IScope?) {
    findCandidate(owner)?.let {
      it.resume(++resumeOrder)

      if (scope != null && it.hasAppliedInitialBackStackUpdate) {
        transferLeaseTo(it, scope)
      }
    }
  }

  /**
   * Records that [owner] paused.
   *
   * Hands the lease to another owner only if that owner is resumed (e.g., a Fragment whose dialog
   * is being dismissed); otherwise keeps it. A paused leaseholder may still be the topmost visible
   * owner (e.g., beneath a translucent window or in picture-in-picture mode), and any other paused
   * owner is beneath it. A displaced owner gets the lease back when it resumes.
   *
   * A `null` scope (e.g., the SDK is disabled) still records the lifecycle transition so the
   * candidate remains eligible for fallback once a scope becomes available.
   *
   * Can throw arbitrary exceptions. See "Exception semantics" in the class KDoc.
   */
  fun pauseCandidate(owner: Any, scope: IScope?) {
    findCandidate(owner)?.let {
      it.pause()

      if (leaseHolder === it) {
        mostRecentlyResumedCandidate(excluding = it, resumedOnly = true)?.let { next ->
          handOffLease(next, scope)
        }
      }
    }
  }

  /**
   * Records that [owner] stopped.
   *
   * As with pausing, hands the lease to another owner only if that owner is resumed; otherwise the
   * stopped owner keeps it. Keeping it lets a lone owner go on recording navigation that happens
   * while it isn't resumed (e.g., while the app is in the background, or after it restarts but
   * before it resumes), including starting nav transactions.
   *
   * A paused owner doesn't get the lease: when the leaseholder stops before it, the app is
   * typically going to the background (e.g., a translucent leaseholder stops before the owner
   * beneath it), and that owner isn't what the user last saw. A paused owner that comes back to the
   * foreground gets the lease when it resumes.
   *
   * A `null` scope (e.g., the SDK is disabled) still records the lifecycle transition so the
   * candidate remains eligible for fallback once a scope becomes available.
   *
   * Can throw arbitrary exceptions. See "Exception semantics" in the class KDoc.
   */
  fun stopCandidate(owner: Any, scope: IScope?) {
    findCandidate(owner)?.let {
      it.stop()

      if (leaseHolder === it) {
        mostRecentlyResumedCandidate(resumedOnly = true)?.let { next -> handOffLease(next, scope) }
      }
    }
  }

  /**
   * Stops tracking [owner] (e.g., because it was destroyed or its effect left the composition).
   * No-ops if `owner` instance isn't registered.
   *
   * Finishes and unbinds `owner`'s nav transaction, and clears its published nav data if that data
   * is the most recently published. If `owner` holds the lease, hands it to the eligible candidate
   * most recently observed resumed, even if that candidate is now only started or paused, or
   * releases it if there's none.
   *
   * A `null` scope (e.g., the SDK is disabled) still removes the candidate, but its transaction is
   * left to time out, its published data is left in place, and the lease is released rather than
   * handed off.
   *
   * Coordinator references are released even if scope cleanup or lease handoff throws. See
   * "Exception semantics" in the class KDoc. The remaining scope cleanup is then skipped.
   */
  fun unregisterCandidate(owner: Any, scope: IScope?) {
    val candidate = findCandidate(owner) ?: return

    try {
      finishTransaction(candidate, scope)
      candidates.remove(candidate)

      if (publishedDataCandidate === candidate) {
        publishedDataCandidate = null
        scope?.let { candidate.onClearPublishedData(it) }
      }

      if (leaseHolder === candidate) {
        handOffLease(mostRecentlyResumedCandidate(), scope)
      }
    } finally {
      // Scope observers and transaction callbacks can throw. Never retain a departing owner
      // just because cleanup failed, and preserve any successor selected during handoff.
      candidates.remove(candidate)
      candidate.transaction = null
      if (publishedDataCandidate === candidate) {
        publishedDataCandidate = null
      }
      if (leaseHolder === candidate) {
        leaseHolder = null
      }
    }
  }

  fun hasLease(owner: Any): Boolean = leaseHolder?.owner === owner

  /**
   * Records that the registered [owner] is about to apply a back-stack update and acquires the nav
   * lease if the owner's lifecycle permits it.
   *
   * An owner that isn't resumed doesn't take the lease from a resumed leaseholder, as it isn't the
   * one the user is interacting with (e.g., a preloaded ViewPager2 page next to the current one).
   * Instead, it acquires the lease when it resumes.
   *
   * If the lease transfers, ownership and transaction binding are updated before the candidate's
   * staged nav data is published. Publication completes before this method returns, so the caller
   * can then apply the back-stack update. Later calls for the same [owner] do nothing.
   *
   * Can throw arbitrary exceptions. See "Exception semantics" in the class KDoc.
   */
  fun onBackStackUpdate(owner: Any, scope: IScope) {
    val candidate = findCandidate(owner) ?: return
    if (candidate.hasAppliedInitialBackStackUpdate) {
      return
    }

    candidate.hasAppliedInitialBackStackUpdate = true

    val holder = leaseHolder
    val wouldDisplaceResumedHolder =
      holder != null &&
        holder.state == Lifecycle.State.RESUMED &&
        candidate.state != Lifecycle.State.RESUMED

    if (candidate.canAcquireLeaseOnFirstUpdate && !wouldDisplaceResumedHolder) {
      transferLeaseTo(candidate, scope)
    }
  }

  /** Whether [transaction] is a nav transaction retained for any registered owner. */
  fun isNavTransaction(transaction: ITransaction): Boolean = candidates.any {
    it.transaction === transaction
  }

  /**
   * Records [transaction] as the nav transaction of [owner], which must hold the lease. Ignored
   * otherwise, as only the leaseholder starts nav transactions.
   *
   * If [owner] loses the lease, the transaction is unbound but retained: it's rebound if [owner]
   * regains the lease while it's still unfinished and the scope is free, and finished when [owner]
   * is [unregistered][unregisterCandidate]. It's forgotten once [owner] retires it; see
   * [releaseTransaction].
   */
  fun retainTransaction(owner: Any, transaction: ITransaction) {
    val candidate = leaseHolder ?: return
    if (candidate.owner !== owner) {
      return
    }
    candidate.transaction = transaction
  }

  /**
   * Forgets [transaction] as the nav transaction of [owner], if it's the one retained. Called when
   * [owner] retires the transaction (e.g., it navigated away), so it's never rebound later.
   *
   * Retiring is distinct from losing the lease: a transaction that's still waiting for child spans
   * isn't [finished][ITransaction.isFinished] yet, so without this, regaining the lease would
   * rebind a transaction whose destination [owner] has already left.
   */
  fun releaseTransaction(owner: Any, transaction: ITransaction) {
    val candidate = findCandidate(owner) ?: return
    if (candidate.transaction === transaction) {
      candidate.transaction = null
    }
  }

  private fun findCandidate(owner: Any): Candidate? = candidates.firstOrNull { it.owner === owner }

  /**
   * The eligible candidate most recently observed resumed, other than [excluding]. With
   * [resumedOnly], only candidates that are resumed right now are considered; otherwise, started
   * and paused ones are too.
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

  /**
   * Hands the lease to [candidate], or releases it given `null`.
   *
   * Maintains the invariant that, while a lease is held, the leaseholder is also the candidate
   * recorded as having published the shared scope's nav data. The invariant holds even when a
   * publish or clear callback throws part way through the handoff. See "Exception semantics" in the
   * class KDoc.
   */
  private fun transferLeaseTo(candidate: Candidate?, scope: IScope) {
    if (leaseHolder === candidate) {
      return
    }

    if (candidate != null) {
      // Unbind another owner's transaction without finishing it, so it can be restored if that
      // owner returns. If this owner's retained transaction has since finished, discard it
      // instead of binding a finished transaction to the shared scope.
      scope.withTransaction { current ->
        if (current != null && candidates.any { it !== candidate && it.transaction === current }) {
          scope.clearTransaction()
        }
      }

      val transaction = candidate.transaction
      if (transaction?.isFinished == true) {
        clearTransactionFromScope(candidate, scope)
        candidate.transaction = null
      } else if (transaction != null) {
        // The navigation lease doesn't give this owner control over an unrelated transaction
        // already bound to the shared scope. Restore the owner's retained nav transaction only if
        // the scope is free or already holds that same transaction.
        scope.withTransaction { current ->
          if (current == null || current === transaction) {
            scope.transaction = transaction
          }
        }
      }
    }

    leaseHolder = candidate
    if (candidate != null) {
      // Clear the previous publisher's nav data before publishing the candidate's, so the
      // previous owner's values don't linger where the candidate has nothing staged to replace
      // them.
      val previousPublisher = publishedDataCandidate
      try {
        if (previousPublisher != null && previousPublisher !== candidate) {
          previousPublisher.onClearPublishedData(scope)
        }
        publishedDataCandidate = candidate
        candidate.onPublishData(scope)
      } finally {
        // Scope observers can throw. A candidate owns the scope's nav data from the moment it
        // takes the lease, so record it as the publisher even if clearing the previous owner's
        // data threw. Leaving the previous owner recorded here would strand both owners' values
        // b/c the next handoff would clear data the candidate may have already replaced, and would
        // never clear the candidate's own.
        publishedDataCandidate = candidate
      }
    }
  }

  /** Finishes and unbinds [candidate]'s nav transaction, then forgets it. */
  private fun finishTransaction(candidate: Candidate, scope: IScope?) {
    // No need to finish the transaction if we don't have a scope (e.g., the SDK is disabled), as
    // the transaction can't be sent and it will time out anyway. Not finishing it has the added
    // advantage of keeping SDK work inside `configureScope`'s error handling.
    if (scope != null) {
      val transaction = candidate.transaction
      if (transaction != null && !transaction.isFinished) {
        transaction.finish(transaction.status ?: SpanStatus.OK)
      }
      clearTransactionFromScope(candidate, scope)
    }

    candidate.transaction = null
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
    val onPublishData: (IScope) -> Unit,
    val onClearPublishedData: (IScope) -> Unit,
    initialState: Lifecycle.State,
    initialResumeOrder: Long,
  ) {

    /** The candidate's lifecycle state, as last observed. */
    var state: Lifecycle.State = initialState
      private set

    /**
     * Whether the candidate has been observed STOPPED since
     * [registration][NavLeaseCoordinator.registerCandidate].
     */
    private var wasStopped: Boolean = false

    var transaction: ITransaction? = null

    /**
     * See [NavLeaseCoordinator.resumeOrder]. Non-zero once the candidate has been observed RESUMED
     * since [registration][NavLeaseCoordinator.registerCandidate].
     */
    var resumeOrder: Long = initialResumeOrder
      private set

    var hasAppliedInitialBackStackUpdate: Boolean = false

    /**
     * Whether the candidate's lifecycle permits its first back stack update since
     * [registration][NavLeaseCoordinator.registerCandidate] to acquire the nav lease, including
     * taking it from another owner that isn't resumed: it's resumed, or it has neither resumed nor
     * stopped since registration (i.e., it's still coming up for the first time). A paused or
     * restarted candidate is returning to the screen rather than arriving on it.
     *
     * This checks the candidate's own lifecycle only; it does not indicate whether the candidate
     * has already applied its first update, nor account for a resumed leaseholder (see
     * [NavLeaseCoordinator.onBackStackUpdate]).
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
  }
}
