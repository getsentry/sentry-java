package io.sentry.apollo3

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.variables
import com.apollographql.apollo3.exception.ApolloHttpException
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryLevel
import io.sentry.SpanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach

class SentryApollo3Interceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null
) : ApolloInterceptor {

    constructor(hub: IHub) : this(hub, null)
    constructor(beforeSpan: BeforeSpanCallback) : this(
        HubAdapter.getInstance(),
        beforeSpan
    )

    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain
    ): Flow<ApolloResponse<D>> {
        val activeSpan = hub.span
        if (activeSpan == null) {
            return chain.proceed(request)
        } else {
            val span = startChild(request, activeSpan)
            val sentryTraceHeader = span.toSentryTrace()

            val modifiedRequest =
                request.newBuilder().addHttpHeader(sentryTraceHeader.name, sentryTraceHeader.value)
                    .build()
            span.setData("operationId", modifiedRequest.operation.id())
            modifiedRequest.scalarAdapters?.let {
                span.setData(
                    "variables",
                    modifiedRequest.operation.variables(it).valueMap.toString()
                )
            }

            return chain.proceed(modifiedRequest).onEach {
                span.status =
                    SpanStatus.fromHttpStatusCode(it.httpInfo?.statusCode, SpanStatus.UNKNOWN)
                finish(span, request, it)
            }.catch { throwable ->
                span.apply {
                    status =
                        if (throwable is ApolloHttpException) SpanStatus.fromHttpStatusCode(
                            throwable.statusCode,
                            SpanStatus.INTERNAL_ERROR
                        ) else SpanStatus.INTERNAL_ERROR
                }
                finish(span, request)
                throw throwable
            }
        }
    }

    private fun <D : Operation.Data> startChild(
        request: ApolloRequest<D>,
        activeSpan: ISpan
    ): ISpan {
        val operation = request.operation.name()
        val operationType = when (request.operation) {
            is Query -> "query"
            is Mutation -> "mutation"
            is Subscription -> "subscription"
            else -> request.operation.javaClass.simpleName
        }
        val description = "$operationType $operation"
        return activeSpan.startChild(operation, description)
    }

    private fun <D : Operation.Data> finish(span: ISpan, request: ApolloRequest<D>, response: ApolloResponse<D>? = null) {
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
         * @param request the Apollo request object
         * @param response the Apollo response object
         */
        fun <D : Operation.Data> execute(span: ISpan, request: ApolloRequest<D>, response: ApolloResponse<D>?): ISpan
    }
}
//
// class SentryApolloInterceptor(
//    private val hub: IHub = HubAdapter.getInstance(),
//    private val beforeSpan: BeforeSpanCallback? = null
// ) : ApolloInterceptor {
//
//    constructor(hub: IHub) : this(hub, null)
//    constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)
//
//    override fun interceptAsync(request: InterceptorRequest, chain: ApolloInterceptorChain, dispatcher: Executor, callBack: CallBack) {
//        val activeSpan = hub.span
//        if (activeSpan == null) {
//            chain.proceedAsync(request, dispatcher, callBack)
//        } else {
//            val span = startChild(request, activeSpan)
//            val sentryTraceHeader = span.toSentryTrace()
//
//            // we have no access to URI, no way to verify tracing origins
//            val headers = request.requestHeaders.toBuilder().addHeader(sentryTraceHeader.name, sentryTraceHeader.value).build()
//            val requestWithHeader = request.toBuilder().requestHeaders(headers).build()
//            span.setData("operationId", requestWithHeader.operation.operationId())
//            span.setData("variables", requestWithHeader.operation.variables().valueMap().toString())
//
//            chain.proceedAsync(
//                requestWithHeader, dispatcher,
//                object : CallBack {
//                    override fun onResponse(response: InterceptorResponse) {
//                        // onResponse is called only for statuses 2xx
//                        span.status = response.httpResponse.map { SpanStatus.fromHttpStatusCode(it.code(), SpanStatus.UNKNOWN) }
//                            .or(SpanStatus.UNKNOWN)
//
//                        finish(span, requestWithHeader, response)
//                        callBack.onResponse(response)
//                    }
//
//                    override fun onFetch(sourceType: FetchSourceType) {
//                        callBack.onFetch(sourceType)
//                    }
//
//                    override fun onFailure(e: ApolloException) {
//                        span.apply {
//                            status = if (e is ApolloHttpException) SpanStatus.fromHttpStatusCode(e.code(), SpanStatus.INTERNAL_ERROR) else SpanStatus.INTERNAL_ERROR
//                            throwable = e
//                        }
//                        finish(span, requestWithHeader)
//                        callBack.onFailure(e)
//                    }
//
//                    override fun onCompleted() {
//                        callBack.onCompleted()
//                    }
//                }
//            )
//        }
//    }
//
//    override fun dispose() {}
//
//    private fun startChild(request: InterceptorRequest, activeSpan: ISpan): ISpan {
//        val operation = request.operation.name().name()
//        val operationType = when (request.operation) {
//            is Query -> "query"
//            is Mutation -> "mutation"
//            is Subscription -> "subscription"
//            else -> request.operation.javaClass.simpleName
//        }
//        val description = "$operationType $operation"
//        return activeSpan.startChild(operation, description)
//    }
//
//    private fun finish(span: ISpan, request: InterceptorRequest, response: InterceptorResponse? = null) {
//        var newSpan: ISpan = span
//        if (beforeSpan != null) {
//            try {
//                newSpan = beforeSpan.execute(span, request, response)
//            } catch (e: Exception) {
//                hub.options.logger.log(SentryLevel.ERROR, "An error occurred while executing beforeSpan on ApolloInterceptor", e)
//            }
//        }
//        newSpan.finish()
//
//        response?.let {
//            if (it.httpResponse.isPresent) {
//                val httpResponse = it.httpResponse.get()
//                val httpRequest = httpResponse.request()
//
//                val breadcrumb = Breadcrumb.http(httpRequest.url().toString(), httpRequest.method(), httpResponse.code())
//
//                httpRequest.body()?.contentLength().ifHasValidLength { contentLength ->
//                    breadcrumb.setData("request_body_size", contentLength)
//                }
//                httpResponse.body()?.contentLength().ifHasValidLength { contentLength ->
//                    breadcrumb.setData("response_body_size", contentLength)
//                }
//
//                val hint = Hint().also {
//                    it.set(APOLLO_REQUEST, httpRequest)
//                    it.set(APOLLO_RESPONSE, httpResponse)
//                }
//                hub.addBreadcrumb(breadcrumb, hint)
//            }
//        }
//    }
//
//    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
//        if (this != null && this != -1L) {
//            fn.invoke(this)
//        }
//    }
//
//    /**
//     * The BeforeSpan callback
//     */
//    fun interface BeforeSpanCallback {
//        /**
//         * Mutates span before being added.
//         *
//         * @param span the span to mutate or drop
//         * @param request the HTTP request executed by okHttp
//         * @param response the HTTP response received by okHttp
//         */
//        fun execute(span: ISpan, request: InterceptorRequest, response: InterceptorResponse?): ISpan
//    }
// }
