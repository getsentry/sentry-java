package io.sentry.graphql22

import graphql.GraphQL
import graphql.GraphQLContext
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.MergedField
import graphql.execution.MergedSelectionSet
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.scalar.GraphqlStringCoercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.graphql.ExceptionReporter
import io.sentry.graphql.NoOpSubscriptionHandler
import io.sentry.graphql.SentryGraphqlInstrumentation
import io.sentry.graphql.SentrySubscriptionHandler
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.RuntimeException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentryInstrumentationTest {

    class Fixture {
        val scopes = mock<IScopes>()
        lateinit var activeSpan: SentryTracer

        fun getSut(isTransactionActive: Boolean = true, dataFetcherThrows: Boolean = false, beforeSpan: SentryGraphqlInstrumentation.BeforeSpanCallback? = null): GraphQL {
            whenever(scopes.options).thenReturn(SentryOptions())
            activeSpan = SentryTracer(TransactionContext("name", "op"), scopes)
            val schema = """
            type Query {
                shows: [Show]
            }

            type Show {
                id: Int
            }
            """.trimIndent()

            val graphQLSchema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schema), buildRuntimeWiring(dataFetcherThrows))
            val graphQL = GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(
                    SentryInstrumentation(
                        beforeSpan,
                        NoOpSubscriptionHandler.getInstance(),
                        true
                    )
                )
                .build()

            if (isTransactionActive) {
                whenever(scopes.span).thenReturn(activeSpan)
            } else {
                whenever(scopes.span).thenReturn(null)
            }

            return graphQL
        }

        private fun buildRuntimeWiring(dataFetcherThrows: Boolean) = RuntimeWiring.newRuntimeWiring()
            .type("Query") {
                it.dataFetcher("shows") {
                    if (dataFetcherThrows) {
                        throw RuntimeException("error")
                    } else {
                        listOf(Show(Random.nextInt()), Show(Random.nextInt()))
                    }
                }
            }.build()
    }

    private val fixture = Fixture()

    @Test
    fun `when transaction is active, creates inner spans`() {
        val sut = fixture.getSut()

        withMockScopes {
            val result = sut.execute("{ shows { id } }")

            assertTrue(result.errors.isEmpty())
            assertEquals(1, fixture.activeSpan.children.size)
            val span = fixture.activeSpan.children.first()
            assertEquals("graphql", span.operation)
            assertEquals("Query.shows", span.description)
            assertEquals("auto.graphql.graphql22", span.spanContext.origin)
            assertTrue(span.isFinished)
            assertEquals(SpanStatus.OK, span.status)
        }
    }

    @Test
    fun `when transaction is active, and data fetcher throws, creates inner spans`() {
        val sut = fixture.getSut(dataFetcherThrows = true)

        withMockScopes {
            val result = sut.execute("{ shows { id } }")

            assertTrue(result.errors.isNotEmpty())
            assertEquals(1, fixture.activeSpan.children.size)
            val span = fixture.activeSpan.children.first()
            assertEquals("graphql", span.operation)
            assertEquals("Query.shows", span.description)
            assertTrue(span.isFinished)
            assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
        }
    }

    @Test
    fun `when transaction is not active, does not create spans`() {
        val sut = fixture.getSut(isTransactionActive = false)

        withMockScopes {
            val result = sut.execute("{ shows { id } }")

            assertTrue(result.errors.isEmpty())
            assertTrue(fixture.activeSpan.children.isEmpty())
        }
    }

    @Test
    fun `beforeSpan can drop spans`() {
        val sut = fixture.getSut(beforeSpan = SentryGraphqlInstrumentation.BeforeSpanCallback { _, _, _ -> null })

        withMockScopes {
            val result = sut.execute("{ shows { id } }")

            assertTrue(result.errors.isEmpty())
            assertEquals(1, fixture.activeSpan.children.size)
            val span = fixture.activeSpan.children.first()
            assertEquals("graphql", span.operation)
            assertEquals("Query.shows", span.description)
            assertNotNull(span.isSampled) {
                assertFalse(it)
            }
        }
    }

    @Test
    fun `beforeSpan can modify spans`() {
        val sut = fixture.getSut(beforeSpan = SentryGraphqlInstrumentation.BeforeSpanCallback { span, _, _ -> span.apply { description = "changed" } })

        withMockScopes {
            val result = sut.execute("{ shows { id } }")

            assertTrue(result.errors.isEmpty())
            assertEquals(1, fixture.activeSpan.children.size)
            val span = fixture.activeSpan.children.first()
            assertEquals("graphql", span.operation)
            assertEquals("changed", span.description)
            assertTrue(span.isFinished)
        }
    }

    @Test
    fun `invokes subscription handler for subscription`() {
        val exceptionReporter = mock<ExceptionReporter>()
        val subscriptionHandler = mock<SentrySubscriptionHandler>()
        whenever(subscriptionHandler.onSubscriptionResult(any(), any(), any(), any())).thenReturn("result modified by subscription handler")
        val operation = OperationDefinition.Operation.SUBSCRIPTION
        val instrumentation = SentryInstrumentation(
            null,
            subscriptionHandler,
            exceptionReporter,
            emptyList()
        )
        val dataFetcher = mock<DataFetcher<Any?>>()
        whenever(dataFetcher.get(any())).thenReturn("raw result")
        val graphQLContext = GraphQLContext.newContext().build()
        val executionStepInfo = ExecutionStepInfo.newExecutionStepInfo().type(
            GraphQLScalarType.newScalar().name("MyResponseType").coercing(
                GraphqlStringCoercing()
            ).build()
        ).build()
        val environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
            .graphQLContext(graphQLContext)
            .executionStepInfo(executionStepInfo)
            .operationDefinition(OperationDefinition.newOperationDefinition().operation(operation).build())
            .build()
        val executionContext = ExecutionContextBuilder.newExecutionContextBuilder()
            .executionId(ExecutionId.generate())
            .graphQLContext(graphQLContext)
            .build()
        val executionStrategyParameters = ExecutionStrategyParameters.newParameters()
            .executionStepInfo(executionStepInfo)
            .fields(MergedSelectionSet.newMergedSelectionSet().build())
            .field(MergedField.newMergedField().addField(Field.newField("myFieldName").build()).build())
            .build()
        val parameters = InstrumentationFieldFetchParameters(
            executionContext,
            { environment },
            executionStrategyParameters,
            false
        )
        val instrumentedDataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, parameters, SentryGraphqlInstrumentation.TracingState())
        val result = instrumentedDataFetcher.get(environment)

        assertNotNull(result)
        assertEquals("result modified by subscription handler", result)
    }

    @Test
    fun `Integration adds itself to integration and package list`() {
        withMockScopes {
            val sut = fixture.getSut()
            assertNotNull(fixture.scopes.options.sdkVersion)
            assert(fixture.scopes.options.sdkVersion!!.integrationSet.contains("GraphQL-v22"))
            val packageInfo =
                fixture.scopes.options.sdkVersion!!.packageSet.firstOrNull { pkg -> pkg.name == "maven:io.sentry:sentry-graphql-22" }
            assertNotNull(packageInfo)
            assert(packageInfo.version == BuildConfig.VERSION_NAME)
        }
    }

    fun withMockScopes(closure: () -> Unit) = Mockito.mockStatic(Sentry::class.java).use {
        it.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
        closure.invoke()
    }

    data class Show(val id: Int)
}
