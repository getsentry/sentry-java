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
import com.apollographql.apollo.request.RequestHeaders
import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint.APOLLO_REQUEST
import io.sentry.TypeCheckHint.APOLLO_RESPONSE
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.TracingUtils
import java.util.Locale
import java.util.concurrent.Executor

private const val TRACE_ORIGIN = "auto.graphql.apollo"

class SentryApolloInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null
) : ApolloInterceptor {

    constructor(hub: IHub) : this(hub, null)
    constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)

    init {
        addIntegrationToSdkVersion("Apollo")
        SentryIntegrationPackageStorage.getInstance().addPackage("maven:io.sentry:sentry-apollo", BuildConfig.VERSION_NAME)
    }

    override fun interceptAsync(request: InterceptorRequest, chain: ApolloInterceptorChain, dispatcher: Executor, callBack: CallBack) {
        val activeSpan = if (io.sentry.util.Platform.isAndroid()) hub.transaction else hub.span
        if (activeSpan == null) {
            val headers = addTracingHeaders(request, null)
            val modifiedRequest = request.toBuilder().requestHeaders(headers).build()
            chain.proceedAsync(modifiedRequest, dispatcher, callBack)
        } else {
            val span = startChild(request, activeSpan)
            span.spanContext.origin = TRACE_ORIGIN

            val headers = addTracingHeaders(request, span)
            val requestWithHeader = request.toBuilder().requestHeaders(headers).build()

            span.setData("operationId", requestWithHeader.operation.operationId())
            span.setData("variables", requestWithHeader.operation.variables().valueMap().toString())

            chain.proceedAsync(
                requestWithHeader,
                dispatcher,
                object : CallBack {
                    override fun onResponse(response: InterceptorResponse) {
                        // onResponse is called only for statuses 2xx
                        val statusCode: Int? = response.httpResponse.map { it.code() }.orNull()
                        if (statusCode != null) {
                            span.status = SpanStatus.fromHttpStatusCode(statusCode, SpanStatus.UNKNOWN)
                            span.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, statusCode)
                        } else {
                            span.status = SpanStatus.UNKNOWN
                        }
                        response.httpResponse.map { it.request().method() }.orNull()?.let {
                            span.setData(
                                SpanDataConvention.HTTP_METHOD_KEY,
                                it.toUpperCase(Locale.ROOT)
                            )
                        }

                        finish(span, requestWithHeader, response)
                        callBack.onResponse(response)
                    }

                    override fun onFetch(sourceType: FetchSourceType) {
                        callBack.onFetch(sourceType)
                    }

                    override fun onFailure(e: ApolloException) {
                        span.apply {
                            status = if (e is ApolloHttpException) {
                                setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, e.code())
                                SpanStatus.fromHttpStatusCode(e.code(), SpanStatus.INTERNAL_ERROR)
                            } else {
                                SpanStatus.INTERNAL_ERROR
                            }
                            throwable = e
                        }
                        finish(span, requestWithHeader)
                        callBack.onFailure(e)
                    }

                    override fun onCompleted() {
                        callBack.onCompleted()
                    }
                }
            )
        }
    }

    override fun dispose() {}

    private fun addTracingHeaders(request: InterceptorRequest, span: ISpan?): RequestHeaders {
        val requestHeaderBuilder = request.requestHeaders.toBuilder()

        if (hub.options.isTraceSampling) {
            // we have no access to URI, no way to verify tracing origins
            TracingUtils.trace(
                hub,
                listOf(request.requestHeaders.headerValue(BaggageHeader.BAGGAGE_HEADER)),
                span
            )?.let { tracingHeaders ->
                requestHeaderBuilder.addHeader(
                    tracingHeaders.sentryTraceHeader.name,
                    tracingHeaders.sentryTraceHeader.value
                )
                tracingHeaders.baggageHeader?.let {
                    requestHeaderBuilder.addHeader(it.name, it.value)
                }
            }
        }

        return requestHeaderBuilder.build()
    }

    private fun startChild(request: InterceptorRequest, activeSpan: ISpan): ISpan {
        val operation = request.operation.name().name()
        val operationType = when (request.operation) {
            is Query -> "query"
            is Mutation -> "mutation"
            is Subscription -> "subscription"
            else -> request.operation.javaClass.simpleName
        }
        val op = "http.graphql.$operationType"
        val description = "$operationType $operation"
        return activeSpan.startChild(op, description)
    }

    private fun finish(span: ISpan, request: InterceptorRequest, response: InterceptorResponse? = null) {
        var newSpan: ISpan? = span
        if (beforeSpan != null) {
            try {
                newSpan = beforeSpan.execute(span, request, response)
            } catch (e: Exception) {
                hub.options.logger.log(SentryLevel.ERROR, "An error occurred while executing beforeSpan on ApolloInterceptor", e)
            }
        }
        if (newSpan == null) {
            // span is dropped
            span.spanContext.sampled = false
        } else {
            span.finish()
        }

        response?.let {
            if (it.httpResponse.isPresent) {
                val httpResponse = it.httpResponse.get()
                val httpRequest = httpResponse.request()

                val breadcrumb = Breadcrumb.http(httpRequest.url().toString(), httpRequest.method(), httpResponse.code())

                httpRequest.body()?.contentLength().ifHasValidLength { contentLength ->
                    breadcrumb.setData("request_body_size", contentLength)
                }
                httpResponse.body()?.contentLength().ifHasValidLength { contentLength ->
                    breadcrumb.setData("response_body_size", contentLength)
                }

                val hint = Hint().apply {
                    set(APOLLO_REQUEST, httpRequest)
                    set(APOLLO_RESPONSE, httpResponse)
                }
                hub.addBreadcrumb(breadcrumb, hint)
            }
        }
    }

    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
        if (this != null && this != -1L) {
            fn.invoke(this)
        }
    }

    /**
     * The BeforeSpan callback
     */
    fun interface BeforeSpanCallback {
        /**
         * Mutates span before being added.
         *
         * @param span the span to mutate or drop
         * @param request the HTTP request executed by okHttp
         * @param response the HTTP response received by okHttp
         */
        fun execute(span: ISpan, request: InterceptorRequest, response: InterceptorResponse?): ISpan?
    }
}
