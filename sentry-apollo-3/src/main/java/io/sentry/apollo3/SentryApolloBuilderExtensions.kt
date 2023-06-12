package io.sentry.apollo3

import com.apollographql.apollo3.ApolloClient
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.apollo3.SentryApollo3HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS

@JvmOverloads
fun ApolloClient.Builder.sentryTracing(
    hub: IHub = HubAdapter.getInstance(),
    beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null,
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(SentryOptions.DEFAULT_PROPAGATION_TARGETS)
): ApolloClient.Builder {
    addInterceptor(SentryApollo3Interceptor())
    addHttpInterceptor(
        SentryApollo3HttpInterceptor(
            hub = hub,
            beforeSpan = beforeSpan,
            captureFailedRequests = captureFailedRequests,
            failedRequestTargets = failedRequestTargets
        )
    )
    return this
}

fun ApolloClient.Builder.sentryTracing(
    beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null,
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(SentryOptions.DEFAULT_PROPAGATION_TARGETS)
): ApolloClient.Builder {
    return sentryTracing(
        beforeSpan = beforeSpan,
        captureFailedRequests = captureFailedRequests,
        failedRequestTargets = failedRequestTargets
    )
}
