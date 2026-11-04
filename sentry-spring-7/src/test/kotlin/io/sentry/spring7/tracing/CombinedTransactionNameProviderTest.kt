package io.sentry.spring7.tracing

import io.sentry.TransactionContext.DEFAULT_TRANSACTION_NAME
import io.sentry.protocol.TransactionNameSource
import jakarta.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.mock

class CombinedTransactionNameProviderTest {
  private val request = mock<HttpServletRequest>()

  @Test
  fun `continues after provider failure`() {
    val failingProvider = TransactionNameProvider { throw RuntimeException("provider failed") }
    val successfulProvider =
      object : TransactionNameProvider {
        override fun provideTransactionName(request: HttpServletRequest) = "GET /users/{id}"

        override fun provideTransactionSource() = TransactionNameSource.ROUTE
      }
    val provider = CombinedTransactionNameProvider(listOf(failingProvider, successfulProvider))

    val result = provider.provideTransactionNameAndSource(request)

    assertThat(result.transactionName).isEqualTo("GET /users/{id}")
    assertThat(result.transactionNameSource).isEqualTo(TransactionNameSource.ROUTE)
  }

  @Test
  fun `uses safe fallback when none succeed after failure`() {
    val failingProvider = TransactionNameProvider { throw RuntimeException("provider failed") }
    val emptyProvider = TransactionNameProvider { null }
    val provider = CombinedTransactionNameProvider(listOf(failingProvider, emptyProvider))

    val result = provider.provideTransactionNameAndSource(request)

    assertThat(result.transactionName).isEqualTo(DEFAULT_TRANSACTION_NAME)
    assertThat(result.transactionNameSource).isEqualTo(TransactionNameSource.CUSTOM)
  }

  @Test
  fun `preserves null when none resolve without failure`() {
    val provider = CombinedTransactionNameProvider(listOf(TransactionNameProvider { null }))

    assertThat(provider.provideTransactionName(request)).isNull()
  }

  @Test
  fun `fatal provider failure propagates`() {
    val failure = OutOfMemoryError("fatal")
    val provider =
      CombinedTransactionNameProvider(listOf(TransactionNameProvider { throw failure }))

    assertThat(assertFailsWith<OutOfMemoryError> { provider.provideTransactionName(request) })
      .isSameAs(failure)
  }
}
