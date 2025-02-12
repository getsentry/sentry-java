package io.sentry.apollo4

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.Subscription
import com.apollographql.apollo.api.variables
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import io.sentry.apollo4.SentryApollo4HttpInterceptor.Companion.SENTRY_APOLLO_4_OPERATION_TYPE
import io.sentry.apollo4.SentryApollo4HttpInterceptor.Companion.SENTRY_APOLLO_4_VARIABLES
import io.sentry.vendor.Base64
import kotlinx.coroutines.flow.Flow

class SentryApollo4Interceptor : ApolloInterceptor {

    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain
    ): Flow<ApolloResponse<D>> {
        val builder = request.newBuilder()
            .addHttpHeader(SENTRY_APOLLO_4_OPERATION_TYPE, Base64.encodeToString(operationType(request).toByteArray(), Base64.NO_WRAP))

        request.scalarAdapters?.let {
            builder.addHttpHeader(SENTRY_APOLLO_4_VARIABLES, Base64.encodeToString(request.operation.variables(it).valueMap.toString().toByteArray(), Base64.NO_WRAP))
        }
        builder.addHttpHeader("X-APOLLO-OPERATION-NAME", request.operation.name())
        builder.addHttpHeader("X-APOLLO-OPERATION-ID", request.operation.id())
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
