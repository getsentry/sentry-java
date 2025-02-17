package io.sentry.apollo4

import com.apollographql.apollo.ApolloClient
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.apollo4.SentryApollo4HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS

@JvmOverloads
fun ApolloClient.Builder.sentryTracing(
    scopes: IScopes = ScopesAdapter.getInstance(),
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
    beforeSpan: SentryApollo4HttpInterceptor.BeforeSpanCallback? = null
): ApolloClient.Builder {
    addInterceptor(SentryApollo4Interceptor())
    addHttpInterceptor(
        SentryApollo4HttpInterceptor(
            scopes = scopes,
            captureFailedRequests = captureFailedRequests,
            failedRequestTargets = failedRequestTargets,
            beforeSpan = beforeSpan
        )
    )
    return this
}

fun ApolloClient.Builder.sentryTracing(
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
    beforeSpan: SentryApollo4HttpInterceptor.BeforeSpanCallback? = null
): ApolloClient.Builder {
    return sentryTracing(
        scopes = ScopesAdapter.getInstance(),
        captureFailedRequests = captureFailedRequests,
        failedRequestTargets = failedRequestTargets,
        beforeSpan = beforeSpan
    )
}
