package io.sentry.apollo3

import com.apollographql.apollo3.ApolloClient
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.apollo3.SentryApollo3HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS

@JvmOverloads
fun ApolloClient.Builder.sentryTracing(
    hub: IHub = HubAdapter.getInstance(),
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
    beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null
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
    captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
    beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null
): ApolloClient.Builder {
    return sentryTracing(
        hub = HubAdapter.getInstance(),
        beforeSpan = beforeSpan,
        captureFailedRequests = captureFailedRequests,
        failedRequestTargets = failedRequestTargets
    )
}
