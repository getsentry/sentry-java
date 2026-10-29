package io.sentry.spring

import io.sentry.ILogger
import io.sentry.TransactionContext.DEFAULT_TRANSACTION_NAME
import io.sentry.spring.tracing.TransactionNameProvider
import javax.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.mock

class TransactionNameProviderUtilsTest {
  private val request = mock<HttpServletRequest>()
  private val logger = mock<ILogger>()

  @Test
  fun `single provider failure uses error fallback`() {
    val failure = RuntimeException("provider failed")
    val provider = TransactionNameProvider { throw failure }

    val result = TransactionNameProviderUtils.provideTransactionName(provider, request, logger)

    assertThat(result).isEqualTo(DEFAULT_TRANSACTION_NAME)
  }

  @Test
  fun `single provider fatal failure propagates`() {
    val failure = OutOfMemoryError("fatal")
    val provider = TransactionNameProvider { throw failure }

    assertThat(
        assertFailsWith<OutOfMemoryError> {
          TransactionNameProviderUtils.provideTransactionName(provider, request, logger)
        }
      )
      .isSameAs(failure)
  }
}
