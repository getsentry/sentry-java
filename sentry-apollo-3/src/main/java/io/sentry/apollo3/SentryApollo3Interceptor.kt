package io.sentry.apollo3

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.variables
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import kotlinx.coroutines.flow.Flow

class SentryApollo3Interceptor : ApolloInterceptor {

    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain
    ): Flow<ApolloResponse<D>> {
        val builder = request.newBuilder()
            .addHttpHeader(SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_OPERATION_TYPE, operationType(request))
            .addHttpHeader(SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_OPERATION_NAME, request.operation.name())

        request.scalarAdapters?.let {
            builder.addHttpHeader(SentryApollo3HttpInterceptor.SENTRY_APOLLO_3_VARIABLES, request.operation.variables(it).valueMap.toString())
        }
        return chain.proceed(builder.build())
    }
}

private fun <D : Operation.Data> operationType(apolloRequest: ApolloRequest<D>) = when (apolloRequest.operation) {
    is Query -> "query"
    is Mutation -> "mutation"
    is Subscription -> "subscription"
    else -> apolloRequest.operation.javaClass.simpleName
}

private val <D : Operation.Data> ApolloRequest<D>.scalarAdapters
    get() = executionContext[CustomScalarAdapters]
