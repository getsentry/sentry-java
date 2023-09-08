package io.sentry.graphql

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResultImpl
import graphql.GraphQLContext
import graphql.GraphqlErrorException
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.MergedField
import graphql.execution.MergedSelectionSet
import graphql.execution.ResultPath
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.scalar.GraphqlStringCoercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.graphql.ExceptionReporter.ExceptionDetails
import io.sentry.graphql.SentryInstrumentation.SENTRY_EXCEPTIONS_CONTEXT_KEY
import io.sentry.graphql.SentryInstrumentation.TracingState
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class SentryInstrumentationAnotherTest {

    class Fixture {
        val hub = mock<IHub>()
        lateinit var activeSpan: SentryTracer
        lateinit var dataFetcher: DataFetcher<Any?>
        lateinit var fieldFetchParameters: InstrumentationFieldFetchParameters
        lateinit var instrumentationExecutionParameters: InstrumentationExecutionParameters
        lateinit var environment: DataFetchingEnvironment
        lateinit var executionContext: ExecutionContext
        lateinit var executionStrategyParameters: ExecutionStrategyParameters
        lateinit var executionStepInfo: ExecutionStepInfo
        lateinit var graphQLContext: GraphQLContext
        lateinit var subscriptionHandler: SentrySubscriptionHandler
        lateinit var exceptionReporter: ExceptionReporter
        internal lateinit var instrumentationState: TracingState
        lateinit var instrumentationExecuteOperationParameters: InstrumentationExecuteOperationParameters
        val query = """query greeting(name: "somename")"""
        val variables = mapOf("variableA" to "value a")

        fun getSut(isTransactionActive: Boolean = true, operation: OperationDefinition.Operation = OperationDefinition.Operation.QUERY, graphQLContextParam: Map<Any?, Any?>? = null, addTransactionToTracingState: Boolean = true): SentryInstrumentation {
            whenever(hub.options).thenReturn(SentryOptions())
            activeSpan = SentryTracer(TransactionContext("name", "op"), hub)

            if (isTransactionActive) {
                whenever(hub.span).thenReturn(activeSpan)
            } else {
                whenever(hub.span).thenReturn(null)
            }

            val defaultGraphQLContext = mapOf(
                SentryInstrumentation.SENTRY_HUB_CONTEXT_KEY to hub
            )
            val mergedField =
                MergedField.newMergedField().addField(Field.newField("myFieldName").build()).build()
            exceptionReporter = mock<ExceptionReporter>()
            subscriptionHandler = mock<SentrySubscriptionHandler>()
            whenever(subscriptionHandler.onSubscriptionResult(any(), any(), any(), any())).thenReturn("result modified by subscription handler")
            val instrumentation = SentryInstrumentation(null, subscriptionHandler, exceptionReporter)
            dataFetcher = mock<DataFetcher<Any?>>()
            whenever(dataFetcher.get(any())).thenReturn("raw result")
            graphQLContext = GraphQLContext.newContext()
                .of(graphQLContextParam ?: defaultGraphQLContext).build()
            val scalarType = GraphQLScalarType.newScalar().name("MyResponseType").coercing(
                GraphqlStringCoercing()
            ).build()
            val field = GraphQLFieldDefinition.newFieldDefinition()
                .name("myQueryFieldName")
                .type(scalarType)
                .build()
            val objectType = GraphQLObjectType.newObject().name("QUERY").field(field).build()
            executionStepInfo = ExecutionStepInfo.newExecutionStepInfo()
                .type(scalarType)
                .fieldContainer(objectType)
                .parentInfo(ExecutionStepInfo.newExecutionStepInfo().type(objectType).build())
                .path(ResultPath.rootPath().segment("child"))
                .field(mergedField)
                .build()
            val operationDefinition = OperationDefinition.newOperationDefinition()
                .operation(operation)
                .name("operation name")
                .build()
            environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .graphQLContext(graphQLContext)
                .executionStepInfo(executionStepInfo)
                .operationDefinition(operationDefinition)
                .build()
            executionContext = ExecutionContextBuilder.newExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .graphQLContext(graphQLContext)
                .operationDefinition(operationDefinition)
                .build()
            executionStrategyParameters = ExecutionStrategyParameters.newParameters()
                .executionStepInfo(executionStepInfo)
                .fields(MergedSelectionSet.newMergedSelectionSet().build())
                .field(mergedField)
                .build()
            instrumentationState = SentryInstrumentation.TracingState().also {
                if (isTransactionActive && addTransactionToTracingState) {
                    it.transaction = activeSpan
                }
            }
            fieldFetchParameters = InstrumentationFieldFetchParameters(executionContext, environment, executionStrategyParameters, false).withNewState(
                instrumentationState
            )
            val executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .graphQLContext(graphQLContextParam ?: defaultGraphQLContext)
                .variables(variables)
                .build()
            val schema = GraphQLSchema.newSchema().query(
                GraphQLObjectType.newObject().name("QueryType").field(
                    field
                ).build()
            ).build()
            instrumentationExecutionParameters = InstrumentationExecutionParameters(executionInput, schema, instrumentationState)
            instrumentationExecuteOperationParameters = InstrumentationExecuteOperationParameters(executionContext)

            return instrumentation
        }
    }

    private val fixture = Fixture()

    @Test
    fun `invokes subscription handler for subscription`() {
        val instrumentation = fixture.getSut(isTransactionActive = false, operation = OperationDefinition.Operation.SUBSCRIPTION)
        val instrumentedDataFetcher = instrumentation.instrumentDataFetcher(fixture.dataFetcher, fixture.fieldFetchParameters)
        val result = instrumentedDataFetcher.get(fixture.environment)

        assertEquals("result modified by subscription handler", result)
        verify(fixture.subscriptionHandler).onSubscriptionResult(eq("raw result"), same(fixture.hub), same(fixture.exceptionReporter), same(fixture.fieldFetchParameters))
    }

    @Test
    fun `invokes subscription handler for subscription if transaction is active`() {
        val instrumentation = fixture.getSut(isTransactionActive = true, operation = OperationDefinition.Operation.SUBSCRIPTION)
        val instrumentedDataFetcher = instrumentation.instrumentDataFetcher(fixture.dataFetcher, fixture.fieldFetchParameters)
        val result = instrumentedDataFetcher.get(fixture.environment)

        assertEquals("result modified by subscription handler", result)
        verify(fixture.subscriptionHandler).onSubscriptionResult(eq("raw result"), same(fixture.hub), same(fixture.exceptionReporter), same(fixture.fieldFetchParameters))
    }

    @Test
    fun `does not invoke subscription handler for query`() {
        val instrumentation = fixture.getSut(isTransactionActive = false, operation = OperationDefinition.Operation.QUERY)
        val instrumentedDataFetcher = instrumentation.instrumentDataFetcher(fixture.dataFetcher, fixture.fieldFetchParameters)
        val result = instrumentedDataFetcher.get(fixture.environment)

        assertEquals("raw result", result)
        verify(fixture.subscriptionHandler, never()).onSubscriptionResult(any(), any(), any(), any())
    }

    @Test
    fun `does not invoke subscription handler for query if transaction is active`() {
        val instrumentation = fixture.getSut(isTransactionActive = false, operation = OperationDefinition.Operation.QUERY)
        val instrumentedDataFetcher = instrumentation.instrumentDataFetcher(fixture.dataFetcher, fixture.fieldFetchParameters)
        val result = instrumentedDataFetcher.get(fixture.environment)

        assertEquals("raw result", result)
        verify(fixture.subscriptionHandler, never()).onSubscriptionResult(any(), any(), any(), any())
    }

    @Test
    fun `does not invoke subscription handler for mutation`() {
        val instrumentation = fixture.getSut(isTransactionActive = false, operation = OperationDefinition.Operation.MUTATION)
        val instrumentedDataFetcher = instrumentation.instrumentDataFetcher(fixture.dataFetcher, fixture.fieldFetchParameters)
        val result = instrumentedDataFetcher.get(fixture.environment)

        assertEquals("raw result", result)
        verify(fixture.subscriptionHandler, never()).onSubscriptionResult(any(), any(), any(), any())
    }

    @Test
    fun `does not invoke subscription handler for mutation if transaction is active`() {
        val instrumentation = fixture.getSut(isTransactionActive = false, operation = OperationDefinition.Operation.MUTATION)
        val instrumentedDataFetcher = instrumentation.instrumentDataFetcher(fixture.dataFetcher, fixture.fieldFetchParameters)
        val result = instrumentedDataFetcher.get(fixture.environment)

        assertEquals("raw result", result)
        verify(fixture.subscriptionHandler, never()).onSubscriptionResult(any(), any(), any(), any())
    }

    @Test
    fun `adds a breadcrumb for operation`() {
        val instrumentation = fixture.getSut()
        instrumentation.beginExecuteOperation(fixture.instrumentationExecuteOperationParameters)
        verify(fixture.hub).addBreadcrumb(
            org.mockito.kotlin.check<Breadcrumb> { breadcrumb ->
                assertEquals("graphql", breadcrumb.type)
                assertEquals("query", breadcrumb.category)
                assertEquals("operation name", breadcrumb.data["operation_name"])
                assertEquals("query", breadcrumb.data["operation_type"])
                assertEquals(fixture.executionContext.executionId.toString(), breadcrumb.data["operation_id"])
            }
        )
    }

    @Test
    fun `adds a breadcrumb for data fetcher`() {
        val instrumentation = fixture.getSut()
        instrumentation.instrumentDataFetcher(fixture.dataFetcher, fixture.fieldFetchParameters).get(fixture.environment)
        verify(fixture.hub).addBreadcrumb(
            org.mockito.kotlin.check<Breadcrumb> { breadcrumb ->
                assertEquals("graphql", breadcrumb.type)
                assertEquals("graphql.fetcher", breadcrumb.category)
                assertEquals("/child", breadcrumb.data["path"])
                assertEquals("myFieldName", breadcrumb.data["field"])
                assertEquals("MyResponseType", breadcrumb.data["type"])
                assertEquals("QUERY", breadcrumb.data["object_type"])
            }
        )
    }

    @Test
    fun `stores hub in context and adds transaction to state`() {
        val instrumentation = fixture.getSut(isTransactionActive = true, operation = OperationDefinition.Operation.MUTATION, graphQLContextParam = emptyMap(), addTransactionToTracingState = false)
        withMockHub {
            instrumentation.beginExecution(fixture.instrumentationExecutionParameters)
            assertSame(fixture.hub, fixture.instrumentationExecutionParameters.graphQLContext.get<IHub>(SentryInstrumentation.SENTRY_HUB_CONTEXT_KEY))
            assertNotNull(fixture.instrumentationState.transaction)
        }
    }

    @Test
    fun `invokes exceptionReporter for error`() {
        val instrumentation = fixture.getSut()
        val executionResult = ExecutionResultImpl.newExecutionResult()
            .data("raw result")
            .addError(
                GraphqlErrorException.newErrorException().message("exception message").errorClassification(
                    ErrorType.ValidationError
                ).build()
            )
            .build()
        val resultFuture = instrumentation.instrumentExecutionResult(executionResult, fixture.instrumentationExecutionParameters)
        verify(fixture.exceptionReporter).captureThrowable(
            org.mockito.kotlin.check<Throwable> {
                assertEquals("exception message", it.message)
            },
            org.mockito.kotlin.check<ExceptionDetails> {
                assertSame(fixture.hub, it.hub)
                assertSame(fixture.query, it.query)
                assertEquals(false, it.isSubscription)
                assertEquals(fixture.variables, it.variables)
            },
            same(executionResult)
        )
        val result = resultFuture.get()
        assertSame(executionResult, result)
    }

    @Test
    fun `invokes exceptionReporter for exceptions in GraphQLContext`() {
        val exception = IllegalStateException("some exception")
        val instrumentation = fixture.getSut(
            graphQLContextParam = mapOf(
                SENTRY_EXCEPTIONS_CONTEXT_KEY to listOf(exception),
                SentryInstrumentation.SENTRY_HUB_CONTEXT_KEY to fixture.hub
            )
        )
        val executionResult = ExecutionResultImpl.newExecutionResult()
            .data("raw result")
            .build()
        val resultFuture = instrumentation.instrumentExecutionResult(executionResult, fixture.instrumentationExecutionParameters)
        verify(fixture.exceptionReporter).captureThrowable(
            org.mockito.kotlin.check<Throwable> {
                assertSame(exception, it)
            },
            org.mockito.kotlin.check<ExceptionDetails> {
                assertSame(fixture.hub, it.hub)
                assertSame(fixture.query, it.query)
                assertEquals(false, it.isSubscription)
                assertEquals(fixture.variables, it.variables)
            },
            same(executionResult)
        )
        val result = resultFuture.get()
        assertSame(executionResult, result)
    }

    @Test
    fun `does not invoke exceptionReporter for certain errors that should be handled by SentryDataFetcherExceptionHandler`() {
        val instrumentation = fixture.getSut()
        val executionResult = ExecutionResultImpl.newExecutionResult()
            .data("raw result")
            .addError(GraphqlErrorException.newErrorException().message("exception message").errorClassification(ErrorType.DataFetchingException).build())
            .addError(GraphqlErrorException.newErrorException().message("exception message").errorClassification(org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR).build())
            .addError(GraphqlErrorException.newErrorException().message("exception message").errorClassification(com.netflix.graphql.types.errors.ErrorType.INTERNAL).build())
            .build()
        val resultFuture = instrumentation.instrumentExecutionResult(executionResult, fixture.instrumentationExecutionParameters)
        verify(fixture.exceptionReporter, never()).captureThrowable(any(), any(), any())
        val result = resultFuture.get()
        assertSame(executionResult, result)
    }

    @Test
    fun `never invokes exceptionReporter if no errors`() {
        val instrumentation = fixture.getSut()
        val executionResult = ExecutionResultImpl.newExecutionResult()
            .data("raw result")
            .build()
        val resultFuture = instrumentation.instrumentExecutionResult(executionResult, fixture.instrumentationExecutionParameters)
        verify(fixture.exceptionReporter, never()).captureThrowable(any(), any(), any())
        val result = resultFuture.get()
        assertSame(executionResult, result)
    }

    fun withMockHub(closure: () -> Unit) = Mockito.mockStatic(Sentry::class.java).use {
        it.`when`<Any> { Sentry.getCurrentHub() }.thenReturn(fixture.hub)
        closure.invoke()
    }

    data class Show(val id: Int)
}
