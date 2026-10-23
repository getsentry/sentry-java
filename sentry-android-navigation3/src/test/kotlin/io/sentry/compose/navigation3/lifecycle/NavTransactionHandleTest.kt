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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NavTransactionHandleTest {

  private val transactionContext = TransactionContext("/Checkout", "navigation")
  private val transactionOptions = TransactionOptions()

  private val app = App().apply { viewNames = listOf("/Checkout") }
  private val navigationContext = mapOf("Back Stack" to listOf(mapOf("entry" to "/Checkout")))

  @Test
  fun `started() - is backed by the given transaction`() {
    val fixture = Fixture.create()

    val handle = NavTransactionHandle.started(fixture.transaction)

    assertThat(handle.transaction).isSameInstanceAs(fixture.transaction)
    assertThat(handle.isAwaitingStart).isFalse()
  }

  @Test
  fun `a started handle reports the transaction's status`() {
    val fixture = Fixture.create()
    whenever(fixture.transaction.status).thenReturn(SpanStatus.CANCELLED)
    val handle = NavTransactionHandle.started(fixture.transaction)

    val status = handle.status

    assertThat(status).isEqualTo(SpanStatus.CANCELLED)
  }

  @Test
  fun `a started handle sets data, contexts, and the app context on the transaction`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.started(fixture.transaction)

    handle.setData("arguments", "id=1")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setApp(app)

    verify(fixture.transaction).setData("arguments", "id=1")
    verify(fixture.transaction).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(app)
  }

  @Test
  fun `a started handle finishes the transaction`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.started(fixture.transaction)

    handle.finish(SpanStatus.OK)

    verify(fixture.transaction).finish(SpanStatus.OK)
  }

  @Test
  fun `deferred() - awaits a start without a transaction`() {
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)

    assertThat(handle.transaction).isNull()
    assertThat(handle.isAwaitingStart).isTrue()
    assertThat(handle.status).isNull()
  }

  @Test
  fun `startDeferred() - starts the transaction with the deferred context and options`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)

    val started = handle.startDeferred(fixture.scopes)

    verify(fixture.scopes).startTransaction(transactionContext, transactionOptions)
    assertThat(started).isSameInstanceAs(fixture.transaction)
    assertThat(handle.transaction).isSameInstanceAs(fixture.transaction)
    assertThat(handle.isAwaitingStart).isFalse()
  }

  @Test
  fun `startDeferred() - replays data, contexts, and the app context set before the start`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)
    handle.setData("arguments", "id=1")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setApp(app)

    handle.startDeferred(fixture.scopes)

    verify(fixture.transaction).setData("arguments", "id=1")
    verify(fixture.transaction).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(app)
  }

  @Test
  fun `startDeferred() - replays only the latest value set for each key`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)
    val latestApp = App().apply { viewNames = listOf("/Receipt") }
    handle.setData("arguments", "id=1")
    handle.setData("arguments", "id=2")
    handle.setApp(app)
    handle.setApp(latestApp)

    handle.startDeferred(fixture.scopes)

    verify(fixture.transaction, never()).setData("arguments", "id=1")
    verify(fixture.transaction).setData("arguments", "id=2")
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(latestApp)
  }

  @Test
  fun `a deferred handle sets data on the transaction once it has started`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)
    handle.startDeferred(fixture.scopes)

    handle.setData("arguments", "id=1")
    handle.setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    handle.setApp(app)

    verify(fixture.transaction).setData("arguments", "id=1")
    verify(fixture.transaction).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
    assertThat(fixture.transaction.contexts.app).isSameInstanceAs(app)
  }

  @Test
  fun `a deferred handle reports the transaction's status once it has started`() {
    val fixture = Fixture.create()
    whenever(fixture.transaction.status).thenReturn(SpanStatus.CANCELLED)
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)
    handle.startDeferred(fixture.scopes)

    val status = handle.status

    assertThat(status).isEqualTo(SpanStatus.CANCELLED)
  }

  @Test
  fun `a deferred handle finishes the transaction once it has started`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)
    handle.startDeferred(fixture.scopes)

    handle.finish(SpanStatus.OK)

    verify(fixture.transaction).finish(SpanStatus.OK)
  }

  @Test
  fun `startDeferred() - returns null when the scopes decline to start a transaction`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)

    val started = handle.startDeferred(fixture.scopes)

    assertThat(started).isNull()
    assertThat(handle.transaction).isNull()
  }

  @Test
  fun `startDeferred() - starts no transaction once the deferred handle has finished`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)
    handle.finish(SpanStatus.OK)

    val started = handle.startDeferred(fixture.scopes)

    assertThat(started).isNull()
    assertThat(handle.isAwaitingStart).isFalse()
    verify(fixture.scopes, never()).startTransaction(any<TransactionContext>(), any())
  }

  @Test
  fun `startDeferred() - starts no transaction a second time`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.deferred(transactionContext, transactionOptions)
    handle.startDeferred(fixture.scopes)

    val started = handle.startDeferred(fixture.scopes)

    assertThat(started).isNull()
    assertThat(handle.transaction).isSameInstanceAs(fixture.transaction)
    verify(fixture.scopes).startTransaction(any<TransactionContext>(), any())
  }

  @Test
  fun `startDeferred() - starts no transaction for a started handle`() {
    val fixture = Fixture.create()
    val handle = NavTransactionHandle.started(fixture.transaction)

    val started = handle.startDeferred(fixture.scopes)

    assertThat(started).isNull()
    verify(fixture.scopes, never()).startTransaction(any<TransactionContext>(), any())
  }

  private class Fixture private constructor(val scopes: IScopes, val transaction: ITransaction) {

    companion object {
      fun create(declinesTransactions: Boolean = false): Fixture {
        val contexts = Contexts()
        val transaction = mock<ITransaction>().also { whenever(it.contexts).thenReturn(contexts) }
        val scopes =
          mock<IScopes>().also {
            whenever(it.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
              .thenReturn(if (declinesTransactions) NoOpTransaction.getInstance() else transaction)
          }
        return Fixture(scopes, transaction)
      }
    }
  }
}
