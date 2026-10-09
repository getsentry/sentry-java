package io.sentry.graphql22

import com.google.common.truth.Truth.assertThat
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
import io.sentry.DataCategory
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.clientreport.DiscardReason
import io.sentry.graphql.ExceptionReporter
import io.sentry.graphql.NoOpSubscriptionHandler
import io.sentry.graphql.SentryGraphqlInstrumentation
import io.sentry.graphql.SentrySubscriptionHandler
import java.lang.RuntimeException
import java.util.concurrent.CompletableFuture
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class SentryInstrumentationTest {
  class Fixture {
    val scopes = mock<IScopes>()
    lateinit var activeSpan: SentryTracer

    fun getSut(
      isTransactionActive: Boolean = true,
      dataFetcherThrows: Boolean = false,
      async: Boolean = false,
      beforeSpan: SentryGraphqlInstrumentation.BeforeSpanCallback? = null,
    ): GraphQL {
      whenever(scopes.options)
        .thenReturn(SentryOptions().apply { dsn = "https://key@sentry.io/proj" })
      activeSpan = SentryTracer(TransactionContext("name", "op"), scopes)
      val schema =
        """
        type Query {
            shows: [Show]
        }

        type Show {
            id: Int
        }
        """
          .trimIndent()

      val graphQLSchema =
        SchemaGenerator()
          .makeExecutableSchema(
            SchemaParser().parse(schema),
            buildRuntimeWiring(dataFetcherThrows, async),
          )
      val graphQL =
        GraphQL.newGraphQL(graphQLSchema)
          .instrumentation(
            SentryInstrumentation(beforeSpan, NoOpSubscriptionHandler.getInstance(), true)
          )
          .build()

      if (isTransactionActive) {
        whenever(scopes.span).thenReturn(activeSpan)
      } else {
        whenever(scopes.span).thenReturn(null)
      }

      return graphQL
    }

    private fun buildRuntimeWiring(dataFetcherThrows: Boolean, async: Boolean) =
      RuntimeWiring.newRuntimeWiring()
        .type("Query") {
          it.dataFetcher("shows") {
            if (dataFetcherThrows) {
              throw RuntimeException("error")
            } else {
              val shows = listOf(Show(Random.nextInt()), Show(Random.nextInt()))
              if (async) CompletableFuture.completedFuture(shows) else shows
            }
          }
        }
        .build()
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
    val sut =
      fixture.getSut(
        beforeSpan = SentryGraphqlInstrumentation.BeforeSpanCallback { _, _, _ -> null }
      )
    val onDiscard = mock<SentryOptions.OnDiscardCallback>()
    fixture.scopes.options.onDiscard = onDiscard
    fixture.activeSpan.spanContext.sampled = true

    withMockScopes {
      val result = sut.execute("{ shows { id } }")

      assertTrue(result.errors.isEmpty())
      assertEquals(1, fixture.activeSpan.children.size)
      val span = fixture.activeSpan.children.first()
      assertEquals("graphql", span.operation)
      assertEquals("Query.shows", span.description)
      assertNotNull(span.isSampled) { assertFalse(it) }
      verifyNoMoreInteractions(onDiscard)
    }
  }

  @Test
  fun `reports callback errors only for sampled spans`() {
    for (sampled in listOf(true, false, null)) {
      val onDiscard = mock<SentryOptions.OnDiscardCallback>()
      val sut =
        fixture.getSut(
          beforeSpan = { span, _, _ ->
            span.spanContext.sampled = false
            throw IllegalStateException("callback failed")
          }
        )
      fixture.activeSpan.spanContext.sampled = sampled
      fixture.scopes.options.onDiscard = onDiscard

      withMockScopes {
        assertThat(sut.execute("{ shows { id } }").errors).isEmpty()
        fixture.activeSpan.finish()
      }

      if (sampled == true) {
        verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
      }
      verifyNoMoreInteractions(onDiscard)
    }
  }

  @Test
  fun `when beforeSpan throws, drops span and preserves result`() {
    val failure = IllegalStateException("callback failed")
    val logger = mock<ILogger>()
    val sut =
      fixture.getSut(
        beforeSpan = { span, _, _ ->
          span.description = "partially modified"
          throw failure
        }
      )
    fixture.scopes.options.isDebug = true
    fixture.scopes.options.setLogger(logger)

    withMockScopes {
      val result = sut.execute("{ shows { id } }")
      assertThat(result.errors).isEmpty()
      assertThat(result.getData<Map<String, Any>>()).containsKey("shows")
      val span = fixture.activeSpan.children.single()
      assertThat(span.isSampled).isFalse()
      assertThat(span.isFinished).isTrue()
      verify(logger)
        .log(
          SentryLevel.ERROR,
          "The beforeSpan callback threw an exception in SentryGraphqlInstrumentation. Dropping span.",
          failure,
        )
    }
  }

  @Test
  fun `when beforeSpan throws, drops async span and preserves result`() {
    val sut =
      fixture.getSut(
        async = true,
        beforeSpan = { _, _, _ ->
          throw IllegalStateException("callback failed")
        },
      )
    withMockScopes {
      val result = sut.execute("{ shows { id } }")
      assertThat(result.errors).isEmpty()
      assertThat(result.getData<Map<String, Any>>()).containsKey("shows")
      val span = fixture.activeSpan.children.single()
      assertThat(span.isSampled).isFalse()
      assertThat(span.isFinished).isTrue()
    }
  }

  @Test
  fun `when beforeSpan throws, preserves data fetcher error`() {
    val sut =
      fixture.getSut(
        dataFetcherThrows = true,
        beforeSpan = { _, _, _ ->
          throw IllegalStateException("callback failed")
        },
      )
    withMockScopes {
      val result = sut.execute("{ shows { id } }")
      assertThat(result.errors).hasSize(1)
      assertThat(result.errors.single().message).contains("error")
      assertThat(result.errors.single().message).doesNotContain("callback failed")
      val span = fixture.activeSpan.children.single()
      assertThat(span.isSampled).isFalse()
      assertThat(span.isFinished).isTrue()
      assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
    }
  }

  @Test
  fun `beforeSpan can modify spans`() {
    val sut =
      fixture.getSut(
        beforeSpan =
          SentryGraphqlInstrumentation.BeforeSpanCallback { span, _, _ ->
            span.apply { description = "changed" }
          }
      )

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
    whenever(subscriptionHandler.onSubscriptionResult(any(), any(), any(), any()))
      .thenReturn("result modified by subscription handler")
    val operation = OperationDefinition.Operation.SUBSCRIPTION
    val instrumentation =
      SentryInstrumentation(null, subscriptionHandler, exceptionReporter, emptyList())
    val dataFetcher = mock<DataFetcher<Any?>>()
    whenever(dataFetcher.get(any())).thenReturn("raw result")
    val graphQLContext = GraphQLContext.newContext().build()
    val executionStepInfo =
      ExecutionStepInfo.newExecutionStepInfo()
        .type(
          GraphQLScalarType.newScalar()
            .name("MyResponseType")
            .coercing(GraphqlStringCoercing())
            .build()
        )
        .build()
    val environment =
      DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
        .graphQLContext(graphQLContext)
        .executionStepInfo(executionStepInfo)
        .operationDefinition(
          OperationDefinition.newOperationDefinition().operation(operation).build()
        )
        .build()
    val executionContext =
      ExecutionContextBuilder.newExecutionContextBuilder()
        .executionId(ExecutionId.generate())
        .graphQLContext(graphQLContext)
        .build()
    val executionStrategyParameters =
      ExecutionStrategyParameters.newParameters()
        .executionStepInfo(executionStepInfo)
        .fields(MergedSelectionSet.newMergedSelectionSet().build())
        .field(MergedField.newMergedField().addField(Field.newField("myFieldName").build()).build())
        .build()
    val parameters =
      InstrumentationFieldFetchParameters(
        executionContext,
        { environment },
        executionStrategyParameters,
        false,
      )
    val instrumentedDataFetcher =
      instrumentation.instrumentDataFetcher(
        dataFetcher,
        parameters,
        SentryGraphqlInstrumentation.TracingState(),
      )
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
        fixture.scopes.options.sdkVersion!!.packageSet.firstOrNull { pkg ->
          pkg.name == "maven:io.sentry:sentry-graphql-22"
        }
      assertNotNull(packageInfo)
      assert(packageInfo.version == BuildConfig.VERSION_NAME)
    }
  }

  fun withMockScopes(closure: () -> Unit) =
    Mockito.mockStatic(Sentry::class.java).use {
      it.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
      closure.invoke()
    }

  data class Show(val id: Int)
}
