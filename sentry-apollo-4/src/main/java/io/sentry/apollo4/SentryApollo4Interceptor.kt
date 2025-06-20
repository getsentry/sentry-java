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
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.vendor.Base64
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Interceptor that adds the GraphQL request information to the outgoing HTTP request's headers so
 * that the information can be accessed by {@link SentryApollo4HttpInterceptor}
 */
class SentryApollo4Interceptor
@JvmOverloads
constructor(@ApiStatus.Internal private val scopes: IScopes = ScopesAdapter.getInstance()) :
  ApolloInterceptor {
  override fun <D : Operation.Data> intercept(
    request: ApolloRequest<D>,
    chain: ApolloInterceptorChain,
  ): Flow<ApolloResponse<D>> {
    val builder =
      request
        .newBuilder()
        .addHttpHeader(OPERATION_ID_HEADER_NAME, encodeHeaderValue(request.operation.id()))
        .addHttpHeader(OPERATION_NAME_HEADER_NAME, encodeHeaderValue(request.operation.name()))
        .addHttpHeader(OPERATION_TYPE_HEADER_NAME, encodeHeaderValue(operationType(request)))

    request.scalarAdapters?.let {
      builder.addHttpHeader(
        VARIABLES_HEADER_NAME,
        encodeHeaderValue(request.operation.variables(it).valueMap.toString()),
      )
    }

    return chain.proceed(builder.build())
  }
}

private fun encodeHeaderValue(value: String): String =
  Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)

private fun <D : Operation.Data> operationType(apolloRequest: ApolloRequest<D>) =
  when (apolloRequest.operation) {
    is Query -> "query"
    is Mutation -> "mutation"
    is Subscription -> "subscription"
    else -> apolloRequest.operation.javaClass.simpleName
  }

private val <D : Operation.Data> ApolloRequest<D>.scalarAdapters
  get() = executionContext[CustomScalarAdapters]
