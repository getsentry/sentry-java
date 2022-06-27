package io.sentry.apollo3

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.http.DefaultHttpRequestComposer
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpRequestComposer
import com.apollographql.apollo3.api.variables

class SentryApollo3RequestComposer(url: String) : HttpRequestComposer {
    private val defaultHttpRequestComposer = DefaultHttpRequestComposer(url)

    override fun <D : Operation.Data> compose(apolloRequest: ApolloRequest<D>): HttpRequest {
        val httpRequest = defaultHttpRequestComposer.compose(apolloRequest)
        val builder = httpRequest.newBuilder()
            .addHeader(SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_OPERATION_TYPE, operationType(apolloRequest))
            .addHeader(SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_OPERATION_NAME, apolloRequest.operation.name())

        apolloRequest.scalarAdapters?.let {
            builder.addHeader(SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_VARIABLES, apolloRequest.operation.variables(it).valueMap.toString())
        }

        return builder.build()
    }

    private fun <D : Operation.Data> operationType(apolloRequest: ApolloRequest<D>) = when (apolloRequest.operation) {
        is Query -> "query"
        is Mutation -> "mutation"
        is Subscription -> "subscription"
        else -> apolloRequest.operation.javaClass.simpleName
    }

    private val <D : Operation.Data> ApolloRequest<D>.scalarAdapters
        get() = executionContext[CustomScalarAdapters]
}
