package io.sentry.compose.navigation3.lifecycle

import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App
import io.sentry.protocol.Contexts
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class NavTransactionTest {

  private val transactionContext = TransactionContext("/Checkout", "navigation")
  private val transactionOptions = TransactionOptions()

  private val app = App().apply { viewNames = listOf("/Checkout") }
  private val navigationContext = mapOf("Back Stack" to listOf(mapOf("entry" to "/Checkout")))

  @Test
  fun `started() - is named after the transaction`() {
    val fixture = Fixture.create()

    val handle = NavTransaction.started(fixture.transaction)

    assertThat(handle.name).isEqualTo("/Checkout")
  }

  @Test
  fun `deferred() - is named after the transaction context`() {
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    assertThat(handle.name).isEqualTo("/Checkout")
  }

  @Test
  fun `transaction - is null for a deferred handle`() {
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    assertThat(handle.transaction).isNull()
  }

  @Test
  fun `transaction - is the backing transaction for a started handle`() {
    val fixture = Fixture.create()

    val handle = NavTransaction.started(fixture.transaction)

    assertThat(handle.transaction).isSameInstanceAs(fixture.transaction)
  }

  @Test
  fun `transaction - is the started transaction once a deferred handle has started`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    handle.start(fixture.scopes)

    assertThat(handle.transaction).isSameInstanceAs(fixture.transaction)
  }

  @Test
  fun `transaction - stays null when the scopes decline to start a deferred handle's transaction`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    handle.start(fixture.scopes)

    assertThat(handle.transaction).isNull()
  }

  @Test
  fun `transaction - is null once a deferred handle is finished before it starts`() {
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    handle.finish(onFinished = {})

    assertThat(handle.transaction).isNull()
  }

  @Test
  fun `a started handle sets data, contexts, and the app context on the transaction`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.started(fixture.transaction)

    handle.setData("arguments", "id=1")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setApp(app)

    verify(fixture.transaction).setData("arguments", "id=1")
    verify(fixture.transaction).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(app)
  }

  @Test
  fun `a deferred handle sets data, contexts, and the app context on the transaction once it has started`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)
    handle.start(fixture.scopes)

    handle.setData("arguments", "id=1")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setApp(app)

    verify(fixture.transaction).setData("arguments", "id=1")
    verify(fixture.transaction).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(app)
  }

  @Test
  fun `start() - starts and returns the transaction with the deferred context and options`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    val started = handle.start(fixture.scopes)

    verify(fixture.scopes).startTransaction(transactionContext, transactionOptions)
    assertThat(started).isSameInstanceAs(fixture.transaction)
  }

  @Test
  fun `start() - replays data, contexts, and the app context set before the start`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)
    handle.setData("arguments", "id=1")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setApp(app)

    handle.start(fixture.scopes)

    verify(fixture.transaction).setData("arguments", "id=1")
    verify(fixture.transaction).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(app)
  }

  @Test
  fun `start() - replays only the latest value set for each key`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)
    val latestNavigationContext = mapOf("Back Stack" to listOf(mapOf("entry" to "/Receipt")))
    val latestApp = App().apply { viewNames = listOf("/Receipt") }
    handle.setData("arguments", "id=1")
    handle.setData("arguments", "id=2")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setContext(NAVIGATION_CONTEXT_KEY, latestNavigationContext)
    handle.setApp(app)
    handle.setApp(latestApp)

    handle.start(fixture.scopes)

    verify(fixture.transaction, never()).setData("arguments", "id=1")
    verify(fixture.transaction).setData("arguments", "id=2")
    verify(fixture.transaction, never()).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    verify(fixture.transaction).setContext(NAVIGATION_CONTEXT_KEY, latestNavigationContext)
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(latestApp)
  }

  @Test
  fun `start() - returns null when the scopes decline to start a transaction`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    val started = handle.start(fixture.scopes)

    assertThat(started).isNull()
  }

  @Test
  fun `start() - starts the transaction on a retry after the scopes declined to start one`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)
    handle.setData("arguments", "id=1")
    handle.start(fixture.scopes)
    fixture.declinesTransactions = false

    val started = handle.start(fixture.scopes)

    assertThat(started).isSameInstanceAs(fixture.transaction)
    verify(fixture.transaction).setData("arguments", "id=1")
  }

  @Test
  fun `start() - starts no transaction a second time`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)
    handle.start(fixture.scopes)

    val started = handle.start(fixture.scopes)

    assertThat(started).isNull()
    verify(fixture.scopes).startTransaction(any<TransactionContext>(), any())
  }

  @Test
  fun `start() - starts no transaction for a started handle`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.started(fixture.transaction)

    val started = handle.start(fixture.scopes)

    assertThat(started).isNull()
    verify(fixture.scopes, never()).startTransaction(any<TransactionContext>(), any())
  }

  @Test
  fun `finish() - finishes a started handle's transaction with the transaction's status`() {
    val fixture = Fixture.create()
    whenever(fixture.transaction.status).thenReturn(SpanStatus.CANCELLED)
    val handle = NavTransaction.started(fixture.transaction)

    handle.finish(onFinished = {})

    verify(fixture.transaction).finish(SpanStatus.CANCELLED)
  }

  @Test
  fun `finish() - finishes a started handle's transaction with OK when the transaction has no status`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.started(fixture.transaction)

    handle.finish(onFinished = {})

    verify(fixture.transaction).finish(SpanStatus.OK)
  }

  @Test
  fun `finish() - passes a started handle's transaction to the callback after finishing it`() {
    val fixture = Fixture.create()
    val onFinished = mock<(ITransaction) -> Unit>()
    val handle = NavTransaction.started(fixture.transaction)

    handle.finish(onFinished)

    inOrder(fixture.transaction, onFinished) {
      verify(fixture.transaction).finish(SpanStatus.OK)
      verify(onFinished).invoke(fixture.transaction)
    }
  }

  @Test
  fun `finish() - doesn't finish a started handle's transaction that has already finished`() {
    val fixture = Fixture.create()
    whenever(fixture.transaction.isFinished).thenReturn(true)
    val handle = NavTransaction.started(fixture.transaction)

    handle.finish(onFinished = {})

    verify(fixture.transaction, never()).finish(any())
  }

  @Test
  fun `finish() - passes a started handle's already finished transaction to the callback`() {
    val fixture = Fixture.create()
    val onFinished = mock<(ITransaction) -> Unit>()
    whenever(fixture.transaction.isFinished).thenReturn(true)
    val handle = NavTransaction.started(fixture.transaction)

    handle.finish(onFinished)

    verify(onFinished).invoke(fixture.transaction)
  }

  @Test
  fun `finish() - finishes a deferred handle's transaction once it has started`() {
    val fixture = Fixture.create()
    val onFinished = mock<(ITransaction) -> Unit>()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)
    handle.start(fixture.scopes)

    handle.finish(onFinished)

    verify(fixture.transaction).finish(SpanStatus.OK)
    verify(onFinished).invoke(fixture.transaction)
  }

  @Test
  fun `finish() - discards a pending handle so that it never starts a transaction`() {
    val fixture = Fixture.create()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    handle.finish(onFinished = {})
    val started = handle.start(fixture.scopes)

    assertThat(started).isNull()
    verify(fixture.scopes, never()).startTransaction(any<TransactionContext>(), any())
  }

  @Test
  fun `finish() - does not call back for a pending handle`() {
    val onFinished = mock<(ITransaction) -> Unit>()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)

    handle.finish(onFinished)

    verifyNoInteractions(onFinished)
  }

  @Test
  fun `a discarded handle ignores later writes and finishes`() {
    val fixture = Fixture.create()
    val onFinished = mock<(ITransaction) -> Unit>()
    val handle = NavTransaction.deferred(transactionContext, transactionOptions)
    handle.finish(onFinished = {})

    handle.setData("arguments", "id=1")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setApp(app)
    handle.finish(onFinished)
    val started = handle.start(fixture.scopes)

    assertThat(started).isNull()
    verifyNoInteractions(fixture.transaction, onFinished)
  }

  private class Fixture private constructor(val scopes: IScopes, val transaction: ITransaction) {

    /** Whether [scopes] declines to start transactions, returning a no-op transaction instead. */
    var declinesTransactions = false

    companion object {
      fun create(declinesTransactions: Boolean = false): Fixture {
        val contexts = Contexts()
        val transaction =
          mock<ITransaction>().also {
            whenever(it.name).thenReturn("/Checkout")
            whenever(it.contexts).thenReturn(contexts)
          }
        val scopes = mock<IScopes>()
        val fixture = Fixture(scopes, transaction)
        fixture.declinesTransactions = declinesTransactions
        whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
          .thenAnswer {
            if (fixture.declinesTransactions) NoOpTransaction.getInstance() else transaction
          }
        return fixture
      }
    }
  }
}
