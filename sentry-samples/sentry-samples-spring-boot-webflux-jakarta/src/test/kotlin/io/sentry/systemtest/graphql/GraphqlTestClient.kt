package io.sentry.systemtest.graphql

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Query
import io.sentry.samples.graphql.GreetingQuery
import kotlinx.coroutines.runBlocking

class GraphqlTestClient(backendUrl: String) {

    val apollo = ApolloClient.Builder()
        .serverUrl("$backendUrl/graphql")
        .addHttpHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
        .build()

    fun greet(name: String): ApolloResponse<GreetingQuery.Data>? {
        return executeQuery(GreetingQuery(name))
    }

    private fun <T : Query.Data> executeQuery(query: Query<T>): ApolloResponse<T>? = runBlocking {
        apollo.query(query).execute()
    }

    private fun <T : Mutation.Data> executeMutation(mutation: Mutation<T>): ApolloResponse<T>? = runBlocking {
        apollo.mutation(mutation).execute()
    }
}
