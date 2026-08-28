package io.sentry.apollo5

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
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Interceptor that adds GraphQL request information to Apollo's execution context so that it can be
 * accessed by {@link SentryApollo5HttpInterceptor}.
 */
class SentryApollo5Interceptor
@JvmOverloads
constructor(@ApiStatus.Internal private val scopes: IScopes = ScopesAdapter.getInstance()) :
  ApolloInterceptor {
  override fun <D : Operation.Data> intercept(
    request: ApolloRequest<D>,
    chain: ApolloInterceptorChain,
  ): Flow<ApolloResponse<D>> {
    val variables =
      request.scalarAdapters?.let { request.operation.variables(it).valueMap.toString() }
    val operationContext =
      SentryApollo5OperationContext(
        operationId = request.operation.id(),
        operationName = request.operation.name(),
        operationType = operationType(request),
        variables = variables,
      )

    return chain.proceed(request.newBuilder().addExecutionContext(operationContext).build())
  }
}

private fun <D : Operation.Data> operationType(apolloRequest: ApolloRequest<D>) =
  when (apolloRequest.operation) {
    is Query -> "query"
    is Mutation -> "mutation"
    is Subscription -> "subscription"
    else -> apolloRequest.operation.javaClass.simpleName
  }

private val <D : Operation.Data> ApolloRequest<D>.scalarAdapters
  get() = executionContext[CustomScalarAdapters]
