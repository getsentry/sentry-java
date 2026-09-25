package io.sentry.graphql

import graphql.ErrorClassification
import graphql.ExecutionResultImpl
import graphql.GraphQLContext
import graphql.GraphQLError
import graphql.GraphqlErrorException
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import io.sentry.IScopes
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryGraphqlInstrumentationTest {
  private val scopes = mock<IScopes>()
  private val exceptionReporter = mock<ExceptionReporter>()
  private val parameters = mock<InstrumentationExecutionParameters>()

  init {
    whenever(parameters.graphQLContext)
      .thenReturn(
        GraphQLContext.newContext()
          .of(SentryGraphqlInstrumentation.SENTRY_SCOPES_CONTEXT_KEY, scopes)
          .build()
      )
  }

  @Test
  fun `ignores error by classification string`() {
    captureError(
      error(CustomErrorClassification("SOME_ERROR", mapOf("type" to "OTHER_ERROR"))),
      ignoredErrorTypes = listOf("SOME_ERROR"),
    )

    verify(exceptionReporter, never()).captureThrowable(any(), any(), any())
  }

  @Test
  fun `ignores error by classification specification type`() {
    captureError(
      error(
        CustomErrorClassification(
          "graphql.validation.interpolation.ResourceBundleMessageInterpolator\$ValidationErrorType@1",
          mapOf("type" to "ExtendedValidationError"),
        )
      ),
      ignoredErrorTypes = listOf("ExtendedValidationError"),
    )

    verify(exceptionReporter, never()).captureThrowable(any(), any(), any())
  }

  @Test
  fun `ignores error by string classification specification`() {
    captureError(
      error(CustomErrorClassification("SOME_ERROR", "SPECIFICATION_ERROR")),
      ignoredErrorTypes = listOf("SPECIFICATION_ERROR"),
    )

    verify(exceptionReporter, never()).captureThrowable(any(), any(), any())
  }

  @Test
  fun `captures error when classification specification has no type`() {
    captureError(error(CustomErrorClassification("SOME_ERROR", mapOf("constraint" to "@Size"))))

    verify(exceptionReporter).captureThrowable(any(), any(), any())
  }

  @Test
  fun `captures error when classification specification is null`() {
    captureError(error(CustomErrorClassification("SOME_ERROR", null)))

    verify(exceptionReporter).captureThrowable(any(), any(), any())
  }

  @Test
  fun `captures error when classification specification type is unexpected`() {
    captureError(error(CustomErrorClassification("SOME_ERROR", mapOf("type" to 42))))

    verify(exceptionReporter).captureThrowable(any(), any(), any())
  }

  @Test
  fun `captures error when classification specification throws`() {
    captureError(error(ThrowingErrorClassification))

    verify(exceptionReporter).captureThrowable(any(), any(), any())
  }

  @Test
  fun `ignores error by extensions when classification is null`() {
    val error = mock<GraphQLError>()
    whenever(error.errorType).thenReturn(null)
    whenever(error.extensions).thenReturn(mapOf("errorType" to "EXTENSION_ERROR"))

    captureError(error, ignoredErrorTypes = listOf("EXTENSION_ERROR"))

    verify(exceptionReporter, never()).captureThrowable(any(), any(), any())
  }

  @Test
  fun `captures non-ignored error`() {
    captureError(
      error(CustomErrorClassification("SOME_ERROR", mapOf("type" to "SPECIFICATION_ERROR"))),
      ignoredErrorTypes = listOf("OTHER_ERROR"),
    )

    verify(exceptionReporter).captureThrowable(any(), any(), any())
  }

  private fun captureError(error: GraphQLError, ignoredErrorTypes: List<String> = emptyList()) {
    val instrumentation =
      SentryGraphqlInstrumentation(
        null,
        NoOpSubscriptionHandler.getInstance(),
        exceptionReporter,
        ignoredErrorTypes,
        "manual.test",
      )
    val result = ExecutionResultImpl.newExecutionResult().addError(error).build()

    instrumentation.instrumentExecutionResultComplete(parameters, result, null)
  }

  private fun error(errorClassification: ErrorClassification): GraphQLError =
    GraphqlErrorException.newErrorException()
      .message("exception message")
      .errorClassification(errorClassification)
      .build()

  private class CustomErrorClassification(
    private val stringValue: String,
    private val specification: Any?,
  ) : ErrorClassification {
    override fun toSpecification(error: GraphQLError): Any? = specification

    override fun toString(): String = stringValue
  }

  private object ThrowingErrorClassification : ErrorClassification {
    override fun toSpecification(error: GraphQLError): Any =
      throw AssertionError("failed to create specification")

    override fun toString(): String = "SOME_ERROR"
  }
}
