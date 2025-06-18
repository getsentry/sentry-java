package io.sentry.apollo3

import com.apollographql.apollo3.ApolloClient
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.apollo3.SentryApollo3HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS

@JvmOverloads
fun ApolloClient.Builder.sentryTracing(
    scopes: IScopes = ScopesAdapter.getInstance(),
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
    beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder {
    addInterceptor(SentryApollo3Interceptor())
    addHttpInterceptor(
        SentryApollo3HttpInterceptor(
            scopes = scopes,
            captureFailedRequests = captureFailedRequests,
            failedRequestTargets = failedRequestTargets,
            beforeSpan = beforeSpan,
        ),
    )
    return this
}

fun ApolloClient.Builder.sentryTracing(
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
    beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder =
    sentryTracing(
        scopes = ScopesAdapter.getInstance(),
        captureFailedRequests = captureFailedRequests,
        failedRequestTargets = failedRequestTargets,
        beforeSpan = beforeSpan,
    )
