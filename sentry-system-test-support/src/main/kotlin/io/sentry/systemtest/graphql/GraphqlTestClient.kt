package io.sentry.systemtest.graphql

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Query
import io.sentry.samples.graphql.AddProjectMutation
import io.sentry.samples.graphql.GreetingQuery
import io.sentry.samples.graphql.ProjectQuery
import io.sentry.samples.graphql.TasksAndAssigneesQuery
import kotlinx.coroutines.runBlocking

class GraphqlTestClient(
    backendUrl: String,
) {
    val apollo =
        ApolloClient
            .Builder()
            .serverUrl("$backendUrl/graphql")
            .addHttpHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
            .build()

    fun greet(name: String): ApolloResponse<GreetingQuery.Data>? = executeQuery(GreetingQuery(name))

    fun project(slug: String): ApolloResponse<ProjectQuery.Data>? = executeQuery(ProjectQuery(slug))

    fun tasksAndAssignees(slug: String): ApolloResponse<TasksAndAssigneesQuery.Data>? = executeQuery(TasksAndAssigneesQuery(slug))

    fun addProject(slug: String): ApolloResponse<AddProjectMutation.Data>? = executeMutation(AddProjectMutation(slug))

    private fun <T : Query.Data> executeQuery(query: Query<T>): ApolloResponse<T>? =
        runBlocking {
            apollo.query(query).execute()
        }

    private fun <T : Mutation.Data> executeMutation(mutation: Mutation<T>): ApolloResponse<T>? =
        runBlocking {
            apollo.mutation(mutation).execute()
        }
}
