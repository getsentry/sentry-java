package io.sentry.apollo

import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.Subscription
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptor.CallBack
import com.apollographql.apollo.interceptor.ApolloInterceptor.FetchSourceType
import com.apollographql.apollo.interceptor.ApolloInterceptor.InterceptorRequest
import com.apollographql.apollo.interceptor.ApolloInterceptor.InterceptorResponse
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryLevel
import io.sentry.SpanStatus
import java.util.concurrent.Executor

class SentryApolloInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null
) : ApolloInterceptor {

    constructor(hub: IHub) : this(hub, null)
    constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)

    override fun interceptAsync(request: InterceptorRequest, chain: ApolloInterceptorChain, dispatcher: Executor, callBack: CallBack) {
        val activeSpan = hub.span
        if (activeSpan == null) {
            chain.proceedAsync(request, dispatcher, callBack)
        } else {
            val span = startChild(request, activeSpan)
            val sentryTraceHeader = span.toSentryTrace()

            // we have no access to URI, no way to verify tracing origins
            val headers = request.requestHeaders.toBuilder().addHeader(sentryTraceHeader.name, sentryTraceHeader.value).build()
            val requestWithHeader = request.toBuilder().requestHeaders(headers).build()

            chain.proceedAsync(requestWithHeader, dispatcher, object : CallBack {
                override fun onResponse(response: InterceptorResponse) {
                    // onResponse is called only for statuses 2xx
                    span.status = response.httpResponse.map { SpanStatus.fromHttpStatusCode(it.code(), SpanStatus.UNKNOWN) }
                        .or(SpanStatus.UNKNOWN)

                    finish(span, requestWithHeader, response)
                    callBack.onResponse(response)
                }

                override fun onFetch(sourceType: FetchSourceType) {
                    callBack.onFetch(sourceType)
                }

                override fun onFailure(e: ApolloException) {
                    span.apply {
                        status = if (e is ApolloHttpException) SpanStatus.fromHttpStatusCode(e.code(), SpanStatus.INTERNAL_ERROR) else SpanStatus.INTERNAL_ERROR
                        throwable = e
                    }
                    finish(span, requestWithHeader)
                    callBack.onFailure(e)
                }

                override fun onCompleted() {
                    callBack.onCompleted()
                }
            })
        }
    }

    override fun dispose() {}

    private fun startChild(request: InterceptorRequest, activeSpan: ISpan): ISpan {
        val operation = request.operation.name().name()
        val operationType = when (request.operation) {
            is Query -> "query"
            is Mutation -> "mutation"
            is Subscription -> "subscription"
            else -> request.operation.javaClass.simpleName
        }
        val description = "$operationType $operation"
        return activeSpan.startChild(operation, description)
    }

    private fun finish(span: ISpan, request: InterceptorRequest, response: InterceptorResponse? = null) {
        var newSpan: ISpan = span
        if (beforeSpan != null) {
            try {
                newSpan = beforeSpan.execute(span, request, response)
            } catch (e: Exception) {
                hub.options.logger.log(SentryLevel.ERROR, "An error occurred while executing beforeSpan on ApolloInterceptor", e)
            }
        }
        newSpan.finish()
    }

    /**
     * The BeforeSpan callback
     */
    interface BeforeSpanCallback {
        /**
         * Mutates span before being added.
         *
         * @param span the span to mutate or drop
         * @param request the HTTP request executed by okHttp
         * @param response the HTTP response received by okHttp
         */
        fun execute(span: ISpan, request: InterceptorRequest, response: InterceptorResponse?): ISpan
    }
}
