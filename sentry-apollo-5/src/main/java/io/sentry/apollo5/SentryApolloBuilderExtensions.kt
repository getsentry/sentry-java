package io.sentry.apollo5

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.interceptor.ApolloInterceptor
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.apollo5.SentryApollo5HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS

@JvmOverloads
fun ApolloClient.Builder.sentryTracing(
  scopes: IScopes = ScopesAdapter.getInstance(),
  captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
  failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
  beforeSpan: SentryApollo5HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder {
  addInterceptor(SentryApollo5Interceptor(), ApolloInterceptor.InsertionPoint.BeforeCache)
  addHttpInterceptor(
    SentryApollo5HttpInterceptor(
      scopes = scopes,
      captureFailedRequests = captureFailedRequests,
      failedRequestTargets = failedRequestTargets,
      beforeSpan = beforeSpan,
    )
  )
  return this
}

fun ApolloClient.Builder.sentryTracing(
  captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
  failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
  beforeSpan: SentryApollo5HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder =
  sentryTracing(
    scopes = ScopesAdapter.getInstance(),
    captureFailedRequests = captureFailedRequests,
    failedRequestTargets = failedRequestTargets,
    beforeSpan = beforeSpan,
  )
