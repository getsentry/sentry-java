package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.ISentryClient
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NavCandidateTest {

  @Test
  fun `start() - advances a created candidate but preserves a resumed candidate`() {
    val created = candidate(Lifecycle.State.CREATED)
    val resumed = candidate(Lifecycle.State.RESUMED, resumeOrder = 1)

    created.start()
    resumed.start()

    assertThat(created.state).isEqualTo(Lifecycle.State.STARTED)
    assertThat(resumed.state).isEqualTo(Lifecycle.State.RESUMED)
  }

  @Test
  fun `resume() - records the supplied order and resumed state`() {
    val candidate = candidate(Lifecycle.State.CREATED)

    candidate.resume(2)

    assertThat(candidate.state).isEqualTo(Lifecycle.State.RESUMED)
    assertThat(candidate.resumeOrder).isEqualTo(2)
  }

  @Test
  fun `a resumed candidate moves through started to created when paused and stopped`() {
    val candidate = candidate(Lifecycle.State.RESUMED, resumeOrder = 1)

    candidate.pause()
    assertThat(candidate.state).isEqualTo(Lifecycle.State.STARTED)
    candidate.stop()
    assertThat(candidate.state).isEqualTo(Lifecycle.State.CREATED)
  }

  @Test
  fun `a new created or started candidate may acquire the lease on its first update`() {
    val created = candidate(Lifecycle.State.CREATED)
    val started = candidate(Lifecycle.State.STARTED)

    assertThat(created.canAcquireLeaseOnFirstUpdate).isTrue()
    assertThat(started.canAcquireLeaseOnFirstUpdate).isTrue()
  }

  @Test
  fun `a paused candidate cannot acquire the lease on its first update`() {
    val candidate = candidate(Lifecycle.State.RESUMED, resumeOrder = 1)

    candidate.pause()

    assertThat(candidate.canAcquireLeaseOnFirstUpdate).isFalse()
  }

  @Test
  fun `a stopped candidate cannot acquire the lease on its first update until it resumes`() {
    val candidate = candidate(Lifecycle.State.CREATED)

    candidate.stop()
    candidate.start()
    assertThat(candidate.canAcquireLeaseOnFirstUpdate).isFalse()
    candidate.resume(1)
    assertThat(candidate.canAcquireLeaseOnFirstUpdate).isTrue()
  }

  @Test
  fun `hasCurrentClient() - accepts an enabled client bound to the scope`() {
    val fixture = Fixture.create()

    assertThat(fixture.candidate.hasCurrentClient(fixture.scope)).isTrue()
  }

  @Test
  fun `hasCurrentClient() - rejects a disabled client`() {
    val fixture = Fixture.create()

    whenever(fixture.client.isEnabled).thenReturn(false)

    assertThat(fixture.candidate.hasCurrentClient(fixture.scope)).isFalse()
  }

  @Test
  fun `hasCurrentClient() - rejects a replaced client`() {
    val fixture = Fixture.create()
    fixture.scope.bindClient(mock())

    assertThat(fixture.candidate.hasCurrentClient(fixture.scope)).isFalse()
  }

  @Test
  fun `hasCurrentClient() - rejects a candidate without a retained client`() {
    val fixture = Fixture.create()
    fixture.candidate.transactionClient = null

    assertThat(fixture.candidate.hasCurrentClient(fixture.scope)).isFalse()
  }

  @Test
  fun `releaseTransaction() - forgets the matching transaction and client`() {
    val fixture = Fixture.create()

    fixture.candidate.releaseTransaction(fixture.transaction)

    assertThat(fixture.candidate.transaction).isNull()
    assertThat(fixture.candidate.transactionClient).isNull()
  }

  @Test
  fun `releaseTransaction() - preserves a different retained transaction`() {
    val fixture = Fixture.create()

    fixture.candidate.releaseTransaction(mock())

    assertThat(fixture.candidate.transaction).isSameInstanceAs(fixture.transaction)
    assertThat(fixture.candidate.transactionClient).isSameInstanceAs(fixture.client)
  }

  @Test
  fun `finishTransaction() - finishes and unbinds an unfinished transaction from the current client`() {
    val fixture = Fixture.create()
    fixture.scope.transaction = fixture.transaction

    fixture.candidate.finishTransaction(fixture.scope)

    verify(fixture.transaction).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.candidate.transaction).isNull()
    assertThat(fixture.candidate.transactionClient).isNull()
  }

  @Test
  fun `finishTransaction() - preserves a transaction's existing status`() {
    val fixture = Fixture.create()
    whenever(fixture.transaction.status).thenReturn(SpanStatus.CANCELLED)

    fixture.candidate.finishTransaction(fixture.scope)

    verify(fixture.transaction).finish(SpanStatus.CANCELLED)
  }

  @Test
  fun `finishTransaction() - leaves a finished transaction unsubmitted`() {
    val fixture = Fixture.create()
    whenever(fixture.transaction.isFinished).thenReturn(true)

    fixture.candidate.finishTransaction(fixture.scope)

    verify(fixture.transaction, never()).finish(any())
    assertThat(fixture.candidate.transaction).isNull()
  }

  @Test
  fun `finishTransaction() - forgets a transaction from a replaced client without finishing`() {
    val fixture = Fixture.create()
    fixture.scope.transaction = fixture.transaction
    fixture.scope.bindClient(mock<ISentryClient> { on { isEnabled }.thenReturn(true) })

    fixture.candidate.finishTransaction(fixture.scope)

    verify(fixture.transaction, never()).finish(any())
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.candidate.transaction).isNull()
  }

  @Test
  fun `finishTransaction() - forgets a transaction from a disabled client without finishing`() {
    val fixture = Fixture.create()
    fixture.scope.transaction = fixture.transaction
    whenever(fixture.client.isEnabled).thenReturn(false)

    fixture.candidate.finishTransaction(fixture.scope)

    verify(fixture.transaction, never()).finish(any())
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.candidate.transaction).isNull()
  }

  @Test
  fun `finishTransaction() - forgets a transaction without a scope and does not finish it`() {
    val fixture = Fixture.create()

    fixture.candidate.finishTransaction(null)

    verify(fixture.transaction, never()).finish(any())
    assertThat(fixture.candidate.transaction).isNull()
  }

  @Test
  fun `clearTransactionFromScope() - leaves an unrelated bound transaction intact`() {
    val fixture = Fixture.create()
    val unrelated = mock<ITransaction>()
    fixture.scope.transaction = unrelated

    fixture.candidate.clearTransactionFromScope(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(unrelated)
  }

  @Test
  fun `clearTransactionFromScope() - unbinds the retained transaction`() {
    val fixture = Fixture.create()
    fixture.scope.transaction = fixture.transaction

    fixture.candidate.clearTransactionFromScope(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.candidate.transaction).isSameInstanceAs(fixture.transaction)
  }

  private fun candidate(state: Lifecycle.State, resumeOrder: Long = 0): NavCandidate =
    NavCandidate(Any(), {}, {}, state, resumeOrder)

  private class Fixture
  private constructor(
    val candidate: NavCandidate,
    val client: ISentryClient,
    val scope: Scope,
    val transaction: ITransaction,
  ) {
    companion object {
      fun create(): Fixture {
        val client = mock<ISentryClient> { on { isEnabled }.thenReturn(true) }
        val scope = Scope(SentryOptions()).apply { bindClient(client) }
        val transaction = mock<ITransaction>()
        val candidate = NavCandidate(Any(), {}, {}, Lifecycle.State.RESUMED, 1)
        candidate.transaction = transaction
        candidate.transactionClient = client
        return Fixture(candidate, client, scope, transaction)
      }
    }
  }
}
