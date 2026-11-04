package io.sentry.compose.navigation3.lifecycle

import com.google.common.truth.Truth.assertThat
import io.sentry.ITransaction
import io.sentry.SpanStatus
import io.sentry.protocol.App
import io.sentry.protocol.Contexts
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NavTransactionTest {

  private val app = App().apply { viewNames = listOf("/Checkout") }
  private val navigationContext = mapOf("Back Stack" to listOf(mapOf("entry" to "/Checkout")))

  @Test
  fun `started() - is named after the transaction`() {
    val fixture = Fixture.create()

    val handle = NavTransaction.started(fixture.transaction)

    assertThat(handle.name).isEqualTo("/Checkout")
  }

  @Test
  fun `transaction - is the backing transaction for a started handle`() {
    val fixture = Fixture.create()

    val handle = NavTransaction.started(fixture.transaction)

    assertThat(handle.transaction).isSameInstanceAs(fixture.transaction)
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

  private class Fixture private constructor(val transaction: ITransaction) {

    companion object {
      fun create(): Fixture {
        val contexts = Contexts()
        val transaction =
          mock<ITransaction>().also {
            whenever(it.name).thenReturn("/Checkout")
            whenever(it.contexts).thenReturn(contexts)
          }
        return Fixture(transaction)
      }
    }
  }
}
