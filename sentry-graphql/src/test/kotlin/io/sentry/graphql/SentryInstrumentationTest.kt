package io.sentry.graphql

import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
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
        val hub = mock<IHub>()
        lateinit var activeSpan: SentryTracer

        fun getSut(isTransactionActive: Boolean = true, dataFetcherThrows: Boolean = false, beforeSpan: SentryInstrumentation.BeforeSpanCallback? = null): GraphQL {
            whenever(hub.options).thenReturn(SentryOptions())
            activeSpan = SentryTracer(TransactionContext("name", "op"), hub)
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
                .instrumentation(SentryInstrumentation(hub, beforeSpan))
                .build()

            if (isTransactionActive) {
                whenever(hub.span).thenReturn(activeSpan)
            } else {
                whenever(hub.span).thenReturn(null)
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

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isEmpty())
        assertEquals(1, fixture.activeSpan.children.size)
        val span = fixture.activeSpan.children.first()
        assertEquals("Query.shows", span.operation)
        assertTrue(span.isFinished)
        assertEquals(SpanStatus.OK, span.status)
    }

    @Test
    fun `when transaction is active, and data fetcher throws, creates inner spans`() {
        val sut = fixture.getSut(dataFetcherThrows = true)

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isNotEmpty())
        assertEquals(1, fixture.activeSpan.children.size)
        val span = fixture.activeSpan.children.first()
        assertEquals("Query.shows", span.operation)
        assertTrue(span.isFinished)
        assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    }

    @Test
    fun `when transaction is not active, does not create spans`() {
        val sut = fixture.getSut(isTransactionActive = false)

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isEmpty())
        assertTrue(fixture.activeSpan.children.isEmpty())
    }

    @Test
    fun `beforeSpan can drop spans`() {
        val sut = fixture.getSut(beforeSpan = SentryInstrumentation.BeforeSpanCallback { _, _, _ -> null })

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isEmpty())
        assertEquals(1, fixture.activeSpan.children.size)
        val span = fixture.activeSpan.children.first()
        assertEquals("Query.shows", span.operation)
        assertNotNull(span.isSampled) {
            assertFalse(it)
        }
    }

    @Test
    fun `beforeSpan can modify spans`() {
        val sut = fixture.getSut(beforeSpan = SentryInstrumentation.BeforeSpanCallback { span, _, _ -> span.apply { description = "changed" } })

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isEmpty())
        assertEquals(1, fixture.activeSpan.children.size)
        val span = fixture.activeSpan.children.first()
        assertEquals("Query.shows", span.operation)
        assertEquals("changed", span.description)
        assertTrue(span.isFinished)
    }

    data class Show(val id: Int)
}
