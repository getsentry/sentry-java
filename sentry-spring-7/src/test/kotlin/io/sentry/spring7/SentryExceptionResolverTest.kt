package io.sentry.spring7

import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.exception.ExceptionMechanismException
import io.sentry.spring7.tracing.TransactionNameProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryExceptionResolverTest {
  private val scopes = mock<IScopes>()
  private val transactionNameProvider = mock<TransactionNameProvider>()

  private val request = mock<HttpServletRequest>()
  private val response = mock<HttpServletResponse>()

  @Test
  fun `when handles exception, sets wrapped exception for event`() {
    val eventCaptor = argumentCaptor<SentryEvent>()
    whenever(scopes.captureEvent(eventCaptor.capture(), any<Hint>())).thenReturn(null)
    val expectedCause = RuntimeException("test")

    SentryExceptionResolver(scopes, transactionNameProvider, 1)
      .resolveException(request, response, null, expectedCause)

    assertThat(eventCaptor.firstValue.throwable).isEqualTo(expectedCause)
    assertThat(eventCaptor.firstValue.throwableMechanism)
      .isInstanceOf(ExceptionMechanismException::class.java)
    with(eventCaptor.firstValue.throwableMechanism as ExceptionMechanismException) {
      assertThat(exceptionMechanism.isHandled).isFalse
      assertThat(exceptionMechanism.type).isEqualTo(SentryExceptionResolver.MECHANISM_TYPE)
      assertThat(throwable).isEqualTo(expectedCause)
      assertThat(thread).isEqualTo(Thread.currentThread())
      assertThat(isSnapshot).isFalse
    }
  }

  @Test
  fun `when handles exception, sets fatal level for event`() {
    val eventCaptor = argumentCaptor<SentryEvent>()
    whenever(scopes.captureEvent(eventCaptor.capture(), any<Hint>())).thenReturn(null)

    SentryExceptionResolver(scopes, transactionNameProvider, 1)
      .resolveException(request, response, null, RuntimeException("test"))

    assertThat(eventCaptor.firstValue.level).isEqualTo(SentryLevel.FATAL)
  }

  @Test
  fun `when handles exception, sets transaction name for event`() {
    val expectedTransactionName = "test-transaction"
    whenever(transactionNameProvider.provideTransactionName(any()))
      .thenReturn(expectedTransactionName)
    val eventCaptor = argumentCaptor<SentryEvent>()
    whenever(scopes.captureEvent(eventCaptor.capture(), any<Hint>())).thenReturn(null)

    SentryExceptionResolver(scopes, transactionNameProvider, 1)
      .resolveException(request, response, null, RuntimeException("test"))

    assertThat(eventCaptor.firstValue.transaction).isEqualTo(expectedTransactionName)
    verify(transactionNameProvider).provideTransactionName(request)
  }

  @Test
  fun `when handles exception, provides spring resolver hint`() {
    val hintCaptor = argumentCaptor<Hint>()
    whenever(scopes.captureEvent(any(), hintCaptor.capture())).thenReturn(null)

    SentryExceptionResolver(scopes, transactionNameProvider, 1)
      .resolveException(request, response, null, RuntimeException("test"))

    with(hintCaptor.firstValue) {
      assertThat(get("springResolver:request")).isEqualTo(request)
      assertThat(get("springResolver:response")).isEqualTo(response)
    }
  }

  @Test
  fun `when custom create event method provided, uses it to capture event`() {
    val expectedEvent = SentryEvent()
    val eventCaptor = argumentCaptor<SentryEvent>()
    whenever(scopes.captureEvent(eventCaptor.capture(), any<Hint>())).thenReturn(null)
    val resolver =
      object : SentryExceptionResolver(scopes, transactionNameProvider, 1) {
        override fun createEvent(request: HttpServletRequest, ex: Exception) = expectedEvent
      }

    resolver.resolveException(request, response, null, RuntimeException("test"))

    assertThat(eventCaptor.firstValue).isEqualTo(expectedEvent)
  }

  @Test
  fun `when custom create hint method provided, uses it to capture event`() {
    val expectedHint = Hint()
    val hintCaptor = argumentCaptor<Hint>()
    whenever(scopes.captureEvent(any(), hintCaptor.capture())).thenReturn(null)
    val resolver =
      object : SentryExceptionResolver(scopes, transactionNameProvider, 1) {
        override fun createHint(request: HttpServletRequest, response: HttpServletResponse) =
          expectedHint
      }

    resolver.resolveException(request, response, null, RuntimeException("test"))

    assertThat(hintCaptor.firstValue).isEqualTo(expectedHint)
  }
}
