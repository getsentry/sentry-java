package io.sentry.graphql

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SpanStatus
import java.lang.RuntimeException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryInstrumentationTest {

    class Fixture {
        val activeSpan = mock<ISpan>()
        val innerSpan = mock<ISpan>()

        fun getSut(isTransactionActive: Boolean = true, dataFetcherThrows: Boolean = false): GraphQL {
            val schema = """
            type Query {
                shows: [Show]
            }

            type Show {
                id: Int
            }
            """.trimIndent()
            val hub = mock<IHub>()

            val graphQLSchema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schema), buildRuntimeWiring(dataFetcherThrows))
            val graphQL = GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(SentryInstrumentation(hub))
                .build()

            if (isTransactionActive) {
                whenever(hub.span).thenReturn(activeSpan)
            } else {
                whenever(hub.span).thenReturn(null)
            }
            whenever(activeSpan.startChild(any())).thenReturn(innerSpan)

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
        verify(fixture.activeSpan).startChild("Query.shows")
        verify(fixture.innerSpan).finish()
        verifyNoMoreInteractions(fixture.activeSpan)
    }

    @Test
    fun `when transaction is active, and data fetcher throws, creates inner spans`() {
        val sut = fixture.getSut(dataFetcherThrows = true)

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isNotEmpty())
        verify(fixture.activeSpan).startChild("Query.shows")
        verify(fixture.innerSpan).finish(SpanStatus.INTERNAL_ERROR)
        verify(fixture.activeSpan).status = SpanStatus.INTERNAL_ERROR
        verifyNoMoreInteractions(fixture.activeSpan)
    }

    @Test
    fun `when transaction is not active, does not create spans`() {
        val sut = fixture.getSut(isTransactionActive = false)

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isEmpty())
        verifyNoMoreInteractions(fixture.activeSpan)
        verifyNoMoreInteractions(fixture.innerSpan)
    }

    data class Show(val id: Int)
}
