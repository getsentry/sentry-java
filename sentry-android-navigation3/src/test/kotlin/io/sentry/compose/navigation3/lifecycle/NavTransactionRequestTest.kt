package io.sentry.compose.navigation3.lifecycle

import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App
import io.sentry.protocol.Contexts
import io.sentry.protocol.TransactionNameSource
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NavTransactionRequestTest {

  private val navigationContext = mapOf("backstack" to listOf(mapOf("route" to "/Checkout")))

  private val scope = Scope(SentryOptions())

  @Test
  fun `forDestination() - reads transaction options when each transaction starts`() {
    val fixture = Fixture.create()
    val options = SentryOptions().apply { idleTimeout = 100L }
    val request =
      NavTransactionRequest.forDestination(
        name = "/Checkout",
        arguments = mapOf("id" to "1"),
        contexts = emptyMap(),
        viewNames = null,
        options = options,
      )

    options.idleTimeout = 200L
    request.start(fixture.scopes, scope)
    options.idleTimeout = 300L
    request.start(fixture.scopes, scope)

    assertThat(fixture.startedOptions.map { it.idleTimeout }).containsExactly(200L, 300L).inOrder()
    assertThat(fixture.startedOptions.map { it.origin })
      .containsExactly("auto.navigation.nav3", "auto.navigation.nav3")
    verify(fixture.startedTransactions.first()).setData("arguments", mapOf("id" to "1"))
  }

  @Test
  fun `start() - starts a navigation transaction named by route with the request's name`() {
    val fixture = Fixture.create()
    val request = fixture.request()

    val transaction = request.start(fixture.scopes, scope)

    val context = fixture.startedContexts.single()
    assertThat(transaction).isSameInstanceAs(fixture.startedTransactions.single())
    assertThat(context.name).isEqualTo("/Checkout")
    assertThat(context.transactionNameSource).isEqualTo(TransactionNameSource.ROUTE)
    assertThat(context.operation).isEqualTo("navigation")
  }

  @Test
  fun `start() - starts each transaction with a new transaction context`() {
    val fixture = Fixture.create()
    val request = fixture.request()

    request.start(fixture.scopes, scope)
    request.start(fixture.scopes, scope)

    val (first, second) = fixture.startedContexts
    assertThat(second).isNotSameInstanceAs(first)
    assertThat(second.spanId).isNotEqualTo(first.spanId)
    assertThat(second.traceId).isNotEqualTo(first.traceId)
  }

  @Test
  fun `start() - starts each transaction with new options from the options factory`() {
    val fixture = Fixture.create()
    val createdOptions = mutableListOf<TransactionOptions>()
    val request = fixture.request(newOptions = { TransactionOptions().also(createdOptions::add) })

    request.start(fixture.scopes, scope)
    request.start(fixture.scopes, scope)

    assertThat(createdOptions).hasSize(2)
    assertThat(fixture.startedOptions).containsExactlyElementsIn(createdOptions).inOrder()
    assertThat(createdOptions[1]).isNotSameInstanceAs(createdOptions[0])
  }

  @Test
  fun `start() - sets the request's data on the transaction`() {
    val fixture = Fixture.create()
    val request = fixture.request(data = mapOf("arguments" to mapOf("id" to "1")))

    val transaction = request.start(fixture.scopes, scope)!!

    verify(transaction).setData("arguments", mapOf("id" to "1"))
  }

  @Test
  fun `start() - sets the request's contexts on the transaction`() {
    val fixture = Fixture.create()
    val request = fixture.request(contexts = mapOf(NAVIGATION_CONTEXT_KEY to navigationContext))

    val transaction = request.start(fixture.scopes, scope)!!

    verify(transaction).setContext(NAVIGATION_CONTEXT_KEY, navigationContext)
  }

  @Test
  fun `start() - sets the view names on a copy of the scope's app context`() {
    val fixture = Fixture.create()
    val scopeApp =
      App().apply {
        appName = "Sample"
        appVersion = "1.2.3"
        viewNames = listOf("/Previous")
      }
    scope.contexts.setApp(scopeApp)
    val request = fixture.request(viewNames = listOf("/Checkout"))

    val transaction = request.start(fixture.scopes, scope)!!

    val transactionApp = transaction.contexts.app
    assertThat(transactionApp).isNotSameInstanceAs(scopeApp)
    assertThat(transactionApp?.viewNames).containsExactly("/Checkout")
    assertThat(transactionApp?.appName).isEqualTo("Sample")
    assertThat(transactionApp?.appVersion).isEqualTo("1.2.3")
    assertThat(scope.contexts.app?.viewNames).containsExactly("/Previous")
  }

  @Test
  fun `start() - copies the scope's app context anew for each transaction`() {
    val fixture = Fixture.create()
    val request = fixture.request(viewNames = listOf("/Checkout"))
    scope.contexts.setApp(App().apply { appVersion = "1.0.0" })
    request.start(fixture.scopes, scope)
    scope.contexts.setApp(App().apply { appVersion = "2.0.0" })

    val transaction = request.start(fixture.scopes, scope)!!

    assertThat(transaction.contexts.app?.appVersion).isEqualTo("2.0.0")
  }

  @Test
  fun `start() - sets the view names on a new app context when the scope has none`() {
    val fixture = Fixture.create()
    val request = fixture.request(viewNames = listOf("/Checkout"))

    val transaction = request.start(fixture.scopes, scope)!!

    assertThat(transaction.contexts.app?.viewNames).containsExactly("/Checkout")
    assertThat(scope.contexts.app).isNull()
  }

  @Test
  fun `start() - leaves the transaction's app context alone without view names`() {
    val fixture = Fixture.create()
    scope.contexts.setApp(App().apply { appName = "Sample" })
    val request = fixture.request(viewNames = null)

    val transaction = request.start(fixture.scopes, scope)!!

    assertThat(transaction.contexts.app).isNull()
  }

  @Test
  fun `start() - returns null when the scopes start a no-op transaction`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val request =
      fixture.request(
        data = mapOf("arguments" to "id=1"),
        contexts = mapOf(NAVIGATION_CONTEXT_KEY to navigationContext),
        viewNames = listOf("/Checkout"),
      )

    val transaction = request.start(fixture.scopes, scope)

    assertThat(transaction).isNull()
  }

  @Test
  fun `start() - sets nothing on a no-op transaction`() {
    val fixture = Fixture.create()
    val noOpTransaction =
      mock<ITransaction>().also {
        whenever(it.isNoOp).thenReturn(true)
        whenever(fixture.scopes.startTransaction(any<TransactionContext>(), any())).thenReturn(it)
      }
    val request = fixture.request(data = mapOf("arguments" to "id=1"))

    request.start(fixture.scopes, scope)

    verify(noOpTransaction, never()).setData(any(), any())
  }

  private class Fixture private constructor() {
    /** Whether [scopes] declines to start transactions, returning a no-op transaction instead. */
    var declinesTransactions = false

    val startedContexts = mutableListOf<TransactionContext>()
    val startedOptions = mutableListOf<TransactionOptions>()
    val startedTransactions = mutableListOf<ITransaction>()

    val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
          .thenAnswer {
            if (declinesTransactions) {
              NoOpTransaction.getInstance()
            } else {
              startedContexts += it.arguments[0] as TransactionContext
              startedOptions += it.arguments[1] as TransactionOptions
              val contexts = Contexts()
              mock<ITransaction>()
                .also { transaction -> whenever(transaction.contexts).thenReturn(contexts) }
                .also(startedTransactions::add)
            }
          }
      }

    fun request(
      newOptions: () -> TransactionOptions = ::TransactionOptions,
      data: Map<String, Any> = emptyMap(),
      contexts: Map<String, Any> = emptyMap(),
      viewNames: List<String>? = null,
    ): NavTransactionRequest =
      NavTransactionRequest(
        name = "/Checkout",
        newOptions = newOptions,
        data = data,
        contexts = contexts,
        viewNames = viewNames,
      )

    companion object {
      fun create(declinesTransactions: Boolean = false): Fixture =
        Fixture().also { it.declinesTransactions = declinesTransactions }
    }
  }
}
