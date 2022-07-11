package io.sentry.apollo3

import com.apollographql.apollo3.ApolloClient
import io.sentry.HubAdapter
import io.sentry.IHub

@JvmOverloads
fun ApolloClient.Builder.sentryTracing(hub: IHub = HubAdapter.getInstance(), beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null): ApolloClient.Builder {
    addInterceptor(SentryApollo3Interceptor())
    addHttpInterceptor(SentryApollo3HttpInterceptor(hub, beforeSpan))
    return this
}

fun ApolloClient.Builder.sentryTracing(beforeSpan: SentryApollo3HttpInterceptor.BeforeSpanCallback? = null): ApolloClient.Builder {
    return sentryTracing(HubAdapter.getInstance(), beforeSpan)
}
