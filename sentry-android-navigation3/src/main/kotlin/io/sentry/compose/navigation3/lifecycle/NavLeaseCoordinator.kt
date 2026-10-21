package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import io.sentry.IScope
import io.sentry.ITransaction

/**
 * Manages a lease in order to determine which [NavScopes] instance can bind nav transactions and
 * publish nav data to a shared [IScope].
 *
 * `NavScopes` instances register as [candidates][NavCandidate] for lease ownership via [registry].
 *
 * Note: "Owner" below refers to the `LifecycleOwner` whose lifecycle events the candidate's
 * `NavScopes` relays.
 *
 * **Exception semantics**
 *
 * Every method in this class with an `IScope` parameter can throw arbitrary exceptions due to:
 *
 * 1. publication and clearing callbacks supplied at registration;
 * 2. the `IScope` the method is handed, whose mutators notify every observer registered through
 *    `SentryOptions.addScopeObserver` – including the host app's – in an unguarded loop; and
 * 3. `finish()` on a retained nav transaction, which may run the host app's event processors.
 *
 * This class restores its own bookkeeping before the exception leaves, so the coordinator remains
 * usable.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. All `NavScopes` instances using this coordinator must
 * confine their access to the same thread.
 */
internal class NavLeaseCoordinator {

  val registry = NavCandidateRegistry()

  /** Candidate responsible for the most recently published nav data in the shared scope. */
  private var publishedDataCandidate: NavCandidate? = null

  private var leaseHolder: NavCandidate? = null

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
    registry.resume(owner)?.let {
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
    registry.pause(owner)?.let {
      if (leaseHolder === it) {
        registry.mostRecentlyResumedCandidate(excluding = it, resumedOnly = true)?.let { next ->
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
    registry.stop(owner)?.let {
      if (leaseHolder === it) {
        registry.mostRecentlyResumedCandidate(resumedOnly = true)?.let { next ->
          handOffLease(next, scope)
        }
      }
    }
  }

  /**
   * Stops tracking [owner] (e.g., because it was destroyed or its effect left the composition).
   * No-ops if `owner` isn't registered.
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
    val candidate = registry.find(owner) ?: return

    try {
      candidate.finishTransaction(scope)
      registry.remove(candidate)

      if (publishedDataCandidate === candidate) {
        publishedDataCandidate = null
        scope?.let { candidate.onClearPublishedData(it) }
      }

      if (leaseHolder === candidate) {
        handOffLease(registry.mostRecentlyResumedCandidate(), scope)
      }
    } finally {
      // Scope observers and transaction callbacks can throw. Never retain a departing owner
      // just because cleanup failed, and preserve any successor selected during handoff.
      registry.remove(candidate)
      candidate.transaction = null
      candidate.transactionClient = null
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
    val candidate = registry.firstBackStackUpdate(owner) ?: return

    val holder = leaseHolder
    val wouldDisplaceResumedHolder =
      holder != null &&
        holder.state == Lifecycle.State.RESUMED &&
        candidate.state != Lifecycle.State.RESUMED

    if (candidate.canAcquireLeaseOnFirstUpdate && !wouldDisplaceResumedHolder) {
      transferLeaseTo(candidate, scope)
    }
  }

  /**
   * Records [transaction] as the nav transaction of [owner], which must hold the lease. Ignored
   * otherwise, as only the leaseholder starts nav transactions.
   *
   * If [owner] loses the lease, the transaction is unbound but retained: it's rebound if [owner]
   * regains the lease while it's still unfinished and the scope is free, and finished when [owner]
   * is [unregistered][unregisterCandidate]. It's forgotten once [owner] retires it through
   * [NavCandidate.releaseTransaction]. The [scope]'s client identifies the SDK instance that
   * started it, so a transaction from a closed or replaced client cannot be rebound after restart.
   */
  fun retainTransaction(owner: Any, transaction: ITransaction, scope: IScope) {
    val candidate = leaseHolder ?: return
    if (candidate.owner !== owner) {
      return
    }
    candidate.transaction = transaction
    candidate.transactionClient = scope.client
  }

  /**
   * Hands the current lease to [next]. Without a [scope], the lease is released instead, as [next]
   * could not publish its staged data.
   */
  private fun handOffLease(next: NavCandidate?, scope: IScope?) {
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
  private fun transferLeaseTo(candidate: NavCandidate?, scope: IScope) {
    if (leaseHolder === candidate) {
      return
    }

    if (candidate != null) {
      // Unbind another owner's transaction without finishing it, so it can be restored if that
      // owner returns. Discard a finished transaction or one from a closed or replaced client
      // instead of binding it to the current shared scope.
      scope.withTransaction { current ->
        if (current != null && registry.anotherCandidateRetains(current, candidate)) {
          scope.clearTransaction()
        }
      }

      val transaction = candidate.transaction
      if (transaction != null && (transaction.isFinished || !candidate.hasCurrentClient(scope))) {
        candidate.clearTransactionFromScope(scope)
        candidate.transaction = null
        candidate.transactionClient = null
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
}
