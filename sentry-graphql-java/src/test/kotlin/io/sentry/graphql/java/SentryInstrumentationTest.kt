package io.sentry.graphql.java

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.sentry.IHub
import io.sentry.ISpan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryInstrumentationTest {

    class Fixture {
        val transaction = mock<ISpan>()
        val innerSpan = mock<ISpan>()

        fun getSut(isTransactionActive: Boolean = true): GraphQL {
            val schema = """
            type Query {
                shows: [Show]
            }

            type Show {
                id: Int
            }
        """.trimIndent()
            val hub = mock<IHub>()

            val graphQLSchema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(schema), buildRuntimeWiring())
            val graphQL = GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(SentryInstrumentation(hub))
                .build()

            if (isTransactionActive) {
                whenever(hub.span).thenReturn(transaction)
            } else {
                whenever(hub.span).thenReturn(null)
            }
            whenever(transaction.startChild(any())).thenReturn(innerSpan)

            return graphQL
        }

        private fun buildRuntimeWiring() = RuntimeWiring.newRuntimeWiring()
            .type("Query") {
                it.dataFetcher("shows") {
                    listOf(Show(Random.nextInt()), Show(Random.nextInt()))
                }
            }.build()
    }

    private val fixture = Fixture()

    @Test
    fun `when transaction is active, creates inner spans`() {
        val sut = fixture.getSut()

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isEmpty())
        verify(fixture.transaction).startChild("Query.shows")
        verify(fixture.innerSpan).finish()
        verify(fixture.transaction).finish()
    }

    @Test
    fun `when transaction is not , does not create spans`() {
        val sut = fixture.getSut(isTransactionActive = false)

        val result = sut.execute("{ shows { id } }")

        assertTrue(result.errors.isEmpty())
        verifyZeroInteractions(fixture.transaction)
        verifyZeroInteractions(fixture.innerSpan)
    }

    data class Show(val id: Int)
}
