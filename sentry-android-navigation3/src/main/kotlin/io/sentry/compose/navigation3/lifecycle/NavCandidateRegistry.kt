package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import io.sentry.IScope
import io.sentry.ISentryClient
import io.sentry.ITransaction
import io.sentry.SpanStatus

/** Tracks nav lease candidates, their lifecycle, and the order in which they last resumed. */
internal class NavCandidateRegistry {

  private val entries = mutableListOf<NavCandidate>()
  private var resumeOrder: Long = 0

  /** Registers [owner] unless it is already registered or [lifecycleState] is destroyed. */
  fun register(
    owner: Any,
    lifecycleState: Lifecycle.State,
    onPublishData: (IScope) -> Unit,
    onClearPublishedData: (IScope) -> Unit,
  ) {
    if (find(owner) != null || lifecycleState == Lifecycle.State.DESTROYED) {
      return
    }

    val initialResumeOrder = if (lifecycleState == Lifecycle.State.RESUMED) ++resumeOrder else 0
    entries +=
      NavCandidate(owner, onPublishData, onClearPublishedData, lifecycleState, initialResumeOrder)
  }

  fun find(owner: Any): NavCandidate? = entries.firstOrNull { it.owner === owner }

  fun remove(candidate: NavCandidate) {
    entries.remove(candidate)
  }

  fun resume(owner: Any): NavCandidate? = find(owner)?.also { it.resume(++resumeOrder) }

  fun pause(owner: Any): NavCandidate? = find(owner)?.also { it.pause() }

  fun stop(owner: Any): NavCandidate? = find(owner)?.also { it.stop() }

  /** Marks [owner]'s first back stack update, returning its candidate only on that first update. */
  fun firstBackStackUpdate(owner: Any): NavCandidate? =
    find(owner)
      ?.takeUnless { it.hasAppliedInitialBackStackUpdate }
      ?.also {
        it.hasAppliedInitialBackStackUpdate = true
      }

  fun anotherCandidateRetains(transaction: ITransaction, excluding: NavCandidate): Boolean =
    entries.any {
      it !== excluding && it.transaction === transaction
    }

  fun retainsTransaction(transaction: ITransaction): Boolean = entries.any {
    it.transaction === transaction
  }

  /**
   * Returns the eligible candidate most recently observed resumed, other than [excluding]. With
   * [resumedOnly], only candidates currently resumed are considered.
   */
  fun mostRecentlyResumedCandidate(
    excluding: NavCandidate? = null,
    resumedOnly: Boolean = false,
  ): NavCandidate? =
    entries
      .filter {
        it !== excluding &&
          it.hasAppliedInitialBackStackUpdate &&
          it.state.isAtLeast(Lifecycle.State.STARTED) &&
          it.resumeOrder > 0 &&
          (!resumedOnly || it.state == Lifecycle.State.RESUMED)
      }
      .maxByOrNull { it.resumeOrder }
}

/** A registered owner and the state needed to decide whether it may hold the nav lease. */
internal class NavCandidate(
  val owner: Any,
  val onPublishData: (IScope) -> Unit,
  val onClearPublishedData: (IScope) -> Unit,
  initialState: Lifecycle.State,
  initialResumeOrder: Long,
) {

  var state: Lifecycle.State = initialState
    private set

  private var wasStopped: Boolean = false

  var transaction: ITransaction? = null
  var transactionClient: ISentryClient? = null

  fun hasCurrentClient(scope: IScope): Boolean =
    transactionClient?.isEnabled == true && transactionClient === scope.client

  fun releaseTransaction(transaction: ITransaction) {
    if (this.transaction === transaction) {
      this.transaction = null
      transactionClient = null
    }
  }

  /** Finishes and unbinds the nav transaction when its originating client is still active. */
  fun finishTransaction(scope: IScope?) {
    if (scope != null) {
      val retained = transaction
      if (retained != null && !retained.isFinished && hasCurrentClient(scope)) {
        retained.finish(retained.status ?: SpanStatus.OK)
      }
      clearTransactionFromScope(scope)
    }

    transaction = null
    transactionClient = null
  }

  fun clearTransactionFromScope(scope: IScope) {
    val retained = transaction ?: return
    scope.withTransaction { current ->
      if (current === retained) {
        scope.clearTransaction()
      }
    }
  }

  var resumeOrder: Long = initialResumeOrder
    private set

  var hasAppliedInitialBackStackUpdate: Boolean = false

  /** A new or resumed owner may take the lease on its first back stack update. */
  val canAcquireLeaseOnFirstUpdate: Boolean
    get() = state == Lifecycle.State.RESUMED || (resumeOrder == 0L && !wasStopped)

  fun start() {
    // Attaching to an already-resumed lifecycle replays ON_START before ON_RESUME.
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
