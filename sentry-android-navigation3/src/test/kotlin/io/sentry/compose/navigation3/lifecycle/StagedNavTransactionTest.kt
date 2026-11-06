package io.sentry.compose.navigation3.lifecycle

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.Scope
import io.sentry.ScopeObserverAdapter
import io.sentry.SentryDateProvider
import io.sentry.SentryLevel
import io.sentry.SentryNanotimeDate
import io.sentry.SentryOptions
import io.sentry.SpanContext
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import java.util.Date
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("LargeClass")
class StagedNavTransactionTest {

  private val request = navTransactionRequest("/Profile")
  private val updatedContexts = mapOf<String, Any>("Navigation" to "updated")

  private val finishedTransaction =
    mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }
  private val hostTransaction = transactionWithOrigin("manual")
  private val uiLoadTransaction = transactionWithOrigin(ACTIVITY_UI_LOAD_ORIGIN)

  @Test
  fun `publishTo() - keeps the started nav transaction when binding it throws`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.transactionObserverThrows = true

    assertThrows(ThrowingScopeObserverException::class.java) {
      stagedNavTransaction.publishTo(fixture.scope)
    }
    fixture.transactionObserverThrows = false
    val started = fixture.startedTransaction("/Profile")
    stagedNavTransaction.finish(fixture.scope)

    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `stage() - starts no transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction

    stagedNavTransaction.stage(request)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `stage() - replaces the current nav transaction without finishing it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val current = fixture.start("/Home")

    stagedNavTransaction.stage(request)
    stagedNavTransaction.finish(fixture.scope)

    verify(current, never()).finish(any())
  }

  @Test
  fun `publishTo() - starts a staged nav transaction and binds it to a free scope`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `publishTo() - starts a staged nav transaction in place of a finished bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = finishedTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `publishTo() - doesn't start a staged nav transaction while another transaction is bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `publishTo() - logs the route when another transaction blocks the nav transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    verify(fixture.logger).log(eq(SentryLevel.DEBUG), any<String>(), eq("/Profile"))
  }

  @Test
  fun `publishTo() - binds nothing when a staged nav transaction's start is declined`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `publishTo() - starts a nav transaction for a request whose staged one was declined`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.declinesTransactions = false

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `publishTo() - binds an unpublished unfinished nav transaction again`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    assertThat(fixture.startedTransactions).containsExactly(started)
  }

  @Test
  fun `publishTo() - keeps the bound nav transaction bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    verify(fixture.logger, never()).log(eq(SentryLevel.DEBUG), any<String>(), any<Any>())
  }

  @Test
  fun `publishTo() - binds an unpublished nav transaction in place of a finished bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = finishedTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
  }

  @Test
  fun `publishTo() - doesn't bind an unpublished nav transaction while another transaction is bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
    stagedNavTransaction.finish(fixture.scope)
    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `publishTo() - forgets an unpublished nav transaction that has finished and keeps another transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    whenever(started.isFinished).thenReturn(true)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `publishTo() - leaves the scope untouched without a nav transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = finishedTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(finishedTransaction)
  }

  @Test
  fun `publishTo() - starts a new nav transaction for the last request once the last one finished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val finished = fixture.start()
    whenever(finished.isFinished).thenReturn(true)

    stagedNavTransaction.publishTo(fixture.scope)

    val restarted = fixture.startedTransactions.last()
    assertThat(fixture.startedTransactions).hasSize(2)
    assertThat(restarted).isNotSameInstanceAs(finished)
    assertThat(restarted.name).isEqualTo("/Profile")
    assertThat(fixture.scope.transaction).isSameInstanceAs(restarted)
  }

  @Test
  fun `publishTo() - starts a nav transaction for a request whose staged one was discarded`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = hostTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `publishTo() - starts a nav transaction for the most recent of several requests`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = hostTransaction
    stagedNavTransaction.stage(navTransactionRequest("/Home"))
    stagedNavTransaction.publishTo(fixture.scope)
    stagedNavTransaction.stage(request)
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
    assertThat(fixture.startedTransactions).hasSize(1)
  }

  @Test
  fun `publishTo() - makes the restarted nav transaction the one that finish() finishes`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val finished = fixture.start()
    whenever(finished.isFinished).thenReturn(true)
    stagedNavTransaction.publishTo(fixture.scope)
    val restarted = fixture.startedTransactions.last()

    stagedNavTransaction.finish(fixture.scope)

    verify(restarted).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `publishTo() - starts no nav transaction while the started one is unfinished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).containsExactly(started)
  }

  @Test
  fun `publishTo() - starts no nav transaction without a request`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `updateRequest() - restarts the nav transaction from the updated request`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val finished = fixture.start()
    whenever(finished.isFinished).thenReturn(true)

    stagedNavTransaction.updateRequest(
      navTransactionRequest("/Profile", contexts = updatedContexts)
    )
    stagedNavTransaction.publishTo(fixture.scope)

    verify(fixture.startedTransactions.last()).setContext("Navigation", "updated")
  }

  @Test
  fun `updateRequest() - starts a staged nav transaction from the updated request`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)

    stagedNavTransaction.updateRequest(
      navTransactionRequest("/Profile", contexts = updatedContexts)
    )
    stagedNavTransaction.publishTo(fixture.scope)

    val started = fixture.startedTransaction("/Profile")
    verify(started).setContext("Navigation", "updated")
    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
  }

  @Test
  fun `updateRequest() - leaves a started nav transaction alone`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.updateRequest(
      navTransactionRequest("/Profile", contexts = updatedContexts)
    )
    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).containsExactly(started)
    verify(started, never()).setContext(any<String>(), any<Any>())
    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
  }

  @Test
  fun `updateRequest() - keeps no request after the nav transaction is finished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.start()
    stagedNavTransaction.finish(fixture.scope)

    stagedNavTransaction.updateRequest(
      navTransactionRequest("/Profile", contexts = updatedContexts)
    )
    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).hasSize(1)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `updateRequest() - keeps no request without a request`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction

    stagedNavTransaction.updateRequest(request)
    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `publishTo() - starts no nav transaction after the nav transaction is finished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.finish(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).containsExactly(started)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `publishTo() - starts no nav transaction after a staged nav transaction is finished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    stagedNavTransaction.finish(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `publishTo() - starts no nav transaction after the nav transaction is forgotten`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    whenever(started.isFinished).thenReturn(true)
    stagedNavTransaction.forget()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).containsExactly(started)
  }

  @Test
  fun `publishTo() - starts no nav transaction while another transaction is bound`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val finished = fixture.start()
    whenever(finished.isFinished).thenReturn(true)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).containsExactly(finished)
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `retryPublishIfBlocked() - starts a nav transaction blocked by a ui load once it leaves the slot`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `retryPublishIfBlocked() - binds an unfinished nav transaction blocked by a ui load once it leaves the slot`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    assertThat(fixture.startedTransactions).containsExactly(started)
  }

  @Test
  fun `retryPublishIfBlocked() - starts a nav transaction blocked by a ui load once it finishes`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val uiLoad = transactionWithOrigin(ACTIVITY_UI_LOAD_ORIGIN)
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoad
    stagedNavTransaction.publishTo(fixture.scope)
    whenever(uiLoad.isFinished).thenReturn(true)

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `retryPublishIfBlocked() - waits while the blocking ui load holds the slot`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(uiLoadTransaction)

    fixture.scope.clearTransaction()
    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `retryPublishIfBlocked() - retries within the retry window`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()
    fixture.nowNanos = UI_LOAD_RETRY_WINDOW_NANOS

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `retryPublishIfBlocked() - retries nothing after the retry window`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()
    fixture.nowNanos = UI_LOAD_RETRY_WINDOW_NANOS + 1

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `retryPublishIfBlocked() - retries nothing once the retry window passes while the ui load holds the slot`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.nowNanos = UI_LOAD_RETRY_WINDOW_NANOS + 1
    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)
    fixture.scope.clearTransaction()
    fixture.nowNanos = 0

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `retryPublishIfBlocked() - retries only once`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.transaction = transactionWithOrigin(ACTIVITY_UI_LOAD_ORIGIN)
    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `retryPublishIfBlocked() - retries nothing after a transaction other than a ui load blocked it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = hostTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `retryPublishIfBlocked() - starts no new nav transaction when the publication wasn't blocked`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.publishTo(fixture.scope)
    whenever(started.isFinished).thenReturn(true)

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).containsExactly(started)
  }

  @Test
  fun `retryPublishIfBlocked() - retries nothing after a start the scopes declined`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    stagedNavTransaction.publishTo(fixture.scope)
    fixture.declinesTransactions = false

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `retryPublishIfBlocked() - retries nothing once the nav transaction is unpublished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `retryPublishIfBlocked() - retries nothing once the nav transaction is finished`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope)
    stagedNavTransaction.finish(fixture.scope)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `retryPublishIfBlocked() - starts nothing after a ui load blocked a publication that can't be retried`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope, canRetry = false)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `retryPublishIfBlocked() - binds nothing after a ui load blocked a rebind that can't be retried`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = uiLoadTransaction
    stagedNavTransaction.publishTo(fixture.scope, canRetry = false)
    fixture.scope.clearTransaction()

    stagedNavTransaction.retryPublishIfBlocked(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `unpublishFrom() - unbinds the started nav transaction without finishing it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.unpublishFrom(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
    verify(started, never()).finish(any())
  }

  @Test
  fun `unpublishFrom() - keeps another bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.unpublishFrom(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `unpublishFrom() - keeps the bound transaction while the nav transaction is staged`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.unpublishFrom(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `finish() - finishes the started nav transaction and unbinds it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.finish(fixture.scope)

    verify(started).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finish() - finishes the nav transaction with its own status`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    whenever(started.status).thenReturn(SpanStatus.CANCELLED)

    stagedNavTransaction.finish(fixture.scope)

    verify(started).finish(SpanStatus.CANCELLED)
  }

  @Test
  fun `finish() - unbinds a nav transaction that already finished without finishing it again`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    whenever(started.isFinished).thenReturn(true)

    stagedNavTransaction.finish(fixture.scope)

    verify(started, never()).finish(any())
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finish() - finishes an unpublished nav transaction and keeps another bound transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    stagedNavTransaction.unpublishFrom(fixture.scope)
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.finish(fixture.scope)

    verify(started).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `finish() - forgets the nav transaction so that publishing never binds it again`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.start()
    stagedNavTransaction.finish(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finish() - discards a staged nav transaction so that publishing never starts it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    stagedNavTransaction.finish(fixture.scope)

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finish() - forgets the nav transaction when unbinding it throws`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()
    fixture.transactionObserverThrows = true

    assertThrows(ThrowingScopeObserverException::class.java) {
      stagedNavTransaction.finish(fixture.scope)
    }
    stagedNavTransaction.finish(fixture.scope)

    // Finished once: the second call has no nav transaction left to finish or unbind.
    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `finish() - leaves the scope untouched without a nav transaction`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    fixture.scope.transaction = hostTransaction

    stagedNavTransaction.finish(fixture.scope)

    verify(hostTransaction, never()).finish(any())
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `forget() - forgets the started nav transaction without finishing or unbinding it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    val started = fixture.start()

    stagedNavTransaction.forget()
    stagedNavTransaction.finish(fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    verify(started, never()).finish(any())
  }

  @Test
  fun `forget() - forgets a staged nav transaction so that publishing never starts it`() {
    val fixture = Fixture.create()
    val stagedNavTransaction = fixture.stagedNavTransaction
    stagedNavTransaction.stage(request)
    stagedNavTransaction.forget()

    stagedNavTransaction.publishTo(fixture.scope)

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  /** Thrown by a scope observer, to stand in for one the host app registered. */
  private class ThrowingScopeObserverException : RuntimeException()

  private fun transactionWithOrigin(origin: String): ITransaction =
    mock<ITransaction>().also {
      whenever(it.spanContext).thenReturn(SpanContext("op").apply { this.origin = origin })
    }

  private class Fixture private constructor() {
    val scope = Scope(SentryOptions())
    val logger = mock<ILogger>()

    /** The time [scopes]' date provider reports, in nanoseconds. */
    var nowNanos = 0L

    /** Whether [scopes] declines to start transactions, returning a no-op transaction instead. */
    var declinesTransactions = false
    val startedTransactions = mutableListOf<ITransaction>()

    /** Whether a scope observer throws when the scope's transaction is bound or unbound. */
    var transactionObserverThrows = false

    val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.options)
          .thenReturn(
            SentryOptions().apply {
              setLogger(this@Fixture.logger)
              isDebug = true
              dateProvider = SentryDateProvider { SentryNanotimeDate(Date(0), nowNanos) }
            }
          )
        whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
          .thenAnswer {
            if (declinesTransactions) {
              NoOpTransaction.getInstance()
            } else {
              val name = (it.arguments[0] as TransactionContext).name
              mock<ITransaction>()
                .also { transaction -> whenever(transaction.name).thenReturn(name) }
                .also(startedTransactions::add)
            }
          }
      }

    val stagedNavTransaction = StagedNavTransaction(scopes)

    init {
      scope.options.addScopeObserver(
        object : ScopeObserverAdapter() {
          override fun setTransaction(transaction: String?) {
            if (transactionObserverThrows) {
              throw ThrowingScopeObserverException()
            }
          }
        }
      )
    }

    fun startedTransaction(name: String): ITransaction = startedTransactions.single {
      it.name == name
    }

    /** Starts a nav transaction for [name], binding it if the scope is free, and returns it. */
    fun start(name: String = "/Profile"): ITransaction {
      stagedNavTransaction.stage(navTransactionRequest(name))
      stagedNavTransaction.publishTo(scope)
      return startedTransaction(name)
    }

    companion object {
      fun create(declinesTransactions: Boolean = false): Fixture =
        Fixture().also { it.declinesTransactions = declinesTransactions }
    }
  }
}

private fun navTransactionRequest(
  name: String,
  contexts: Map<String, Any> = emptyMap(),
): NavTransactionRequest =
  NavTransactionRequest(
    name = name,
    newOptions = ::TransactionOptions,
    contexts = contexts,
  )
