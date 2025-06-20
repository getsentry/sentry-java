package io.sentry.graphql

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphqlErrorException
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.scalar.GraphqlStringCoercing
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExceptionReporterTest {
  class Fixture {
    val defaultOptions =
      SentryOptions().also {
        it.isSendDefaultPii = true
        it.maxRequestBodySize = SentryOptions.RequestSize.ALWAYS
      }
    val exception = IllegalStateException("some exception")
    val scopes = mock<IScopes>()
    lateinit var instrumentationExecutionParameters: InstrumentationExecutionParameters
    lateinit var executionResult: ExecutionResult
    lateinit var scope: IScope
    val query = """query greeting(name: "somename")"""
    val variables = mapOf("variableA" to "value a")

    fun getSut(
      options: SentryOptions = defaultOptions,
      captureRequestBodyForNonSubscriptions: Boolean = true,
    ): ExceptionReporter {
      whenever(scopes.options).thenReturn(options)
      scope = Scope(options)
      val exceptionReporter = ExceptionReporter(captureRequestBodyForNonSubscriptions)
      executionResult =
        ExecutionResultImpl.newExecutionResult()
          .data("raw result")
          .addError(
            GraphqlErrorException.newErrorException()
              .message("exception message")
              .errorClassification(ErrorType.ValidationError)
              .build()
          )
          .build()
      val executionInput =
        ExecutionInput.newExecutionInput()
          .query(query)
          .graphQLContext(emptyMap<Any?, Any?>())
          .variables(variables)
          .build()
      val scalarType =
        GraphQLScalarType.newScalar()
          .name("MyResponseType")
          .coercing(GraphqlStringCoercing())
          .build()
      val field =
        GraphQLFieldDefinition.newFieldDefinition()
          .name("myQueryFieldName")
          .type(scalarType)
          .build()
      val schema =
        GraphQLSchema.newSchema()
          .query(GraphQLObjectType.newObject().name("QueryType").field(field).build())
          .build()
      val instrumentationState = SentryGraphqlInstrumentation.TracingState()
      instrumentationExecutionParameters =
        InstrumentationExecutionParameters(executionInput, schema, instrumentationState)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
        .whenever(scopes)
        .configureScope(any())

      return exceptionReporter
    }
  }

  private val fixture = Fixture()

  @Test
  fun `captures throwable`() {
    val exceptionReporter = fixture.getSut()
    exceptionReporter.captureThrowable(
      fixture.exception,
      ExceptionReporter.ExceptionDetails(
        fixture.scopes,
        fixture.instrumentationExecutionParameters,
        false,
      ),
      fixture.executionResult,
    )

    verify(fixture.scopes)
      .captureEvent(
        org.mockito.kotlin.check {
          val ex = it.throwableMechanism as ExceptionMechanismException
          assertFalse(ex.exceptionMechanism.isHandled!!)
          assertSame(fixture.exception, ex.throwable)
          assertEquals("GraphqlInstrumentation", ex.exceptionMechanism.type)
          assertNotNull(it.request)
          val request = it.request!!
          val data = request.data as Map<Any, Any>
          assertEquals(fixture.variables, data["variables"])
          assertEquals(fixture.query, data["query"])
          assertEquals("graphql", request.apiTarget)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `uses requests on scope as base`() {
    val exceptionReporter = fixture.getSut()
    val headers = mapOf("some-header" to "some-header-value")
    fixture.scope.request = Request().also { it.headers = headers }
    exceptionReporter.captureThrowable(
      fixture.exception,
      ExceptionReporter.ExceptionDetails(
        fixture.scopes,
        fixture.instrumentationExecutionParameters,
        false,
      ),
      fixture.executionResult,
    )

    verify(fixture.scopes)
      .captureEvent(
        org.mockito.kotlin.check {
          val ex = it.throwableMechanism as ExceptionMechanismException
          assertFalse(ex.exceptionMechanism.isHandled!!)
          assertSame(fixture.exception, ex.throwable)
          assertEquals("GraphqlInstrumentation", ex.exceptionMechanism.type)
          assertSame(fixture.scope.request, it.request)
          assertEquals("graphql", it.request!!.apiTarget)
        },
        any<Hint>(),
      )

    assertNotNull(fixture.scope.request)
    val request = fixture.scope.request!!
    val data = request.data as Map<Any, Any>
    assertEquals(fixture.variables, data["variables"])
    assertEquals(headers, request.headers)
  }

  @Test
  fun `does not attach query or variables if spring`() {
    val exceptionReporter = fixture.getSut(captureRequestBodyForNonSubscriptions = false)
    exceptionReporter.captureThrowable(
      fixture.exception,
      ExceptionReporter.ExceptionDetails(
        fixture.scopes,
        fixture.instrumentationExecutionParameters,
        false,
      ),
      fixture.executionResult,
    )

    verify(fixture.scopes)
      .captureEvent(
        org.mockito.kotlin.check {
          val ex = it.throwableMechanism as ExceptionMechanismException
          assertFalse(ex.exceptionMechanism.isHandled!!)
          assertSame(fixture.exception, ex.throwable)
          assertEquals("GraphqlInstrumentation", ex.exceptionMechanism.type)
          assertNotNull(it.request)
          val request = it.request!!
          assertNull(request.data)
          assertEquals("graphql", request.apiTarget)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `does not attach query or variables if no max body size is set`() {
    val exceptionReporter =
      fixture.getSut(SentryOptions().also { it.isSendDefaultPii = true }, false)
    exceptionReporter.captureThrowable(
      fixture.exception,
      ExceptionReporter.ExceptionDetails(
        fixture.scopes,
        fixture.instrumentationExecutionParameters,
        false,
      ),
      fixture.executionResult,
    )

    verify(fixture.scopes)
      .captureEvent(
        org.mockito.kotlin.check {
          val ex = it.throwableMechanism as ExceptionMechanismException
          assertFalse(ex.exceptionMechanism.isHandled!!)
          assertSame(fixture.exception, ex.throwable)
          assertEquals("GraphqlInstrumentation", ex.exceptionMechanism.type)
          assertNotNull(it.request)
          val request = it.request!!
          assertNull(request.data)
          assertEquals("graphql", request.apiTarget)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `does not attach query or variables if sendDefaultPii is false`() {
    val exceptionReporter =
      fixture.getSut(
        SentryOptions().also { it.maxRequestBodySize = SentryOptions.RequestSize.ALWAYS },
        false,
      )
    exceptionReporter.captureThrowable(
      fixture.exception,
      ExceptionReporter.ExceptionDetails(
        fixture.scopes,
        fixture.instrumentationExecutionParameters,
        false,
      ),
      fixture.executionResult,
    )

    verify(fixture.scopes)
      .captureEvent(
        org.mockito.kotlin.check {
          val ex = it.throwableMechanism as ExceptionMechanismException
          assertFalse(ex.exceptionMechanism.isHandled!!)
          assertSame(fixture.exception, ex.throwable)
          assertEquals("GraphqlInstrumentation", ex.exceptionMechanism.type)
          assertNotNull(it.request)
          val request = it.request!!
          assertNull(request.data)
          assertEquals("graphql", request.apiTarget)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `attaches query and variables if spring and subscription`() {
    val exceptionReporter = fixture.getSut(captureRequestBodyForNonSubscriptions = false)
    exceptionReporter.captureThrowable(
      fixture.exception,
      ExceptionReporter.ExceptionDetails(
        fixture.scopes,
        fixture.instrumentationExecutionParameters,
        true,
      ),
      fixture.executionResult,
    )

    verify(fixture.scopes)
      .captureEvent(
        org.mockito.kotlin.check {
          val ex = it.throwableMechanism as ExceptionMechanismException
          assertFalse(ex.exceptionMechanism.isHandled!!)
          assertSame(fixture.exception, ex.throwable)
          assertEquals("GraphqlInstrumentation", ex.exceptionMechanism.type)
          assertNotNull(it.request)
          val request = it.request!!
          val data = request.data as Map<Any, Any>
          assertEquals(fixture.variables, data["variables"])
          assertEquals(fixture.query, data["query"])
          assertEquals("graphql", request.apiTarget)
        },
        any<Hint>(),
      )
  }
}
