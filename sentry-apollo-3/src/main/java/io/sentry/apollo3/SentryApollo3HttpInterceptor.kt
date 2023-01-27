package io.sentry.apollo3

import com.apollographql.apollo3.api.http.HttpHeader
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.exception.ApolloHttpException
import com.apollographql.apollo3.exception.ApolloNetworkException
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryLevel
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint
import io.sentry.util.PropagationTargetsUtils
import io.sentry.util.UrlUtils

class SentryApollo3HttpInterceptor @JvmOverloads constructor(private val hub: IHub = HubAdapter.getInstance(), private val beforeSpan: BeforeSpanCallback? = null) :
    HttpInterceptor {

    override suspend fun intercept(
        request: HttpRequest,
        chain: HttpInterceptorChain
    ): HttpResponse {
        val activeSpan = hub.span
        return if (activeSpan == null) {
            chain.proceed(request)
        } else {
            val span = startChild(request, activeSpan)

            var cleanedHeaders = removeSentryInternalHeaders(request.headers).toMutableList()

            if (!span.isNoOp && PropagationTargetsUtils.contain(hub.options.tracePropagationTargets, request.url)) {
                val sentryTraceHeader = span.toSentryTrace()
                val baggageHeader = span.toBaggageHeader(request.headers.filter { it.name == BaggageHeader.BAGGAGE_HEADER }.map { it.value })
                cleanedHeaders.add(HttpHeader(sentryTraceHeader.name, sentryTraceHeader.value))

                baggageHeader?.let { newHeader ->
                    cleanedHeaders = cleanedHeaders.filterNot { it.name == BaggageHeader.BAGGAGE_HEADER }.toMutableList().apply {
                        add(HttpHeader(newHeader.name, newHeader.value))
                    }
                }
            }

            val requestBuilder = request.newBuilder().apply {
                headers(cleanedHeaders)
            }

            val modifiedRequest = requestBuilder.build()
            var httpResponse: HttpResponse? = null
            var statusCode: Int? = null

            try {
                httpResponse = chain.proceed(modifiedRequest)
                statusCode = httpResponse.statusCode
                span.status = SpanStatus.fromHttpStatusCode(statusCode, SpanStatus.UNKNOWN)
                return httpResponse
            } catch (e: Throwable) {
                when (e) {
                    is ApolloHttpException -> {
                        statusCode = e.statusCode
                        span.status = SpanStatus.fromHttpStatusCode(statusCode, SpanStatus.INTERNAL_ERROR)
                    }
                    is ApolloNetworkException -> span.status = SpanStatus.INTERNAL_ERROR
                    else -> SpanStatus.INTERNAL_ERROR
                }
                span.throwable = e
                throw e
            } finally {
                finish(span, modifiedRequest, httpResponse, statusCode)
            }
        }
    }

    private fun removeSentryInternalHeaders(headers: List<HttpHeader>): List<HttpHeader> {
        return headers.filterNot { it.name == SENTRY_APOLLO_3_VARIABLES || it.name == SENTRY_APOLLO_3_OPERATION_NAME || it.name == SENTRY_APOLLO_3_OPERATION_TYPE }
    }

    private fun startChild(request: HttpRequest, activeSpan: ISpan): ISpan {
        val urlDetails = UrlUtils.parse(request.url)
        val method = request.method

        val operationName = operationNameFromHeaders(request)
        val operation = operationName ?: "apollo.client"
        val operationType = request.valueForHeader(SENTRY_APOLLO_3_OPERATION_TYPE) ?: method
        val operationId = request.valueForHeader("X-APOLLO-OPERATION-ID")
        val variables = request.valueForHeader(SENTRY_APOLLO_3_VARIABLES)
        val description = "$operationType ${operationName ?: urlDetails.urlOrFallback}"

        return activeSpan.startChild(operation, description).apply {
            urlDetails.applyToSpan(this)

            operationId?.let {
                setData("operationId", it)
            }

            variables?.let {
                setData("variables", it)
            }
        }
    }

    private fun operationNameFromHeaders(request: HttpRequest): String? {
        return request.valueForHeader(SENTRY_APOLLO_3_OPERATION_NAME) ?: request.valueForHeader("X-APOLLO-OPERATION-NAME")
    }

    private fun HttpRequest.valueForHeader(key: String) = headers.firstOrNull { it.name == key }?.value

    private fun finish(span: ISpan, request: HttpRequest, response: HttpResponse? = null, statusCode: Int?) {
        if (beforeSpan != null) {
            try {
                val result = beforeSpan.execute(span, request, response)
                if (result == null) {
                    // Span is dropped
                    span.spanContext.sampled = false
                }
            } catch (e: Throwable) {
                hub.options.logger.log(SentryLevel.ERROR, "An error occurred while executing beforeSpan on ApolloInterceptor", e)
            }
        }
        span.finish()

        val breadcrumb = Breadcrumb.http(request.url, request.method.name, statusCode)

        request.body?.contentLength.ifHasValidLength { contentLength ->
            breadcrumb.setData("request_body_size", contentLength)
        }

        val hint = Hint().also {
            it.set(TypeCheckHint.APOLLO_REQUEST, request)
        }

        response?.let { httpResponse ->
            // Content-Length header is not present on batched operations
            httpResponse.headersContentLength().ifHasValidLength { contentLength ->
                breadcrumb.setData("response_body_size", contentLength)
            }

            if (!breadcrumb.data.containsKey("response_body_size")) {
                httpResponse.body?.buffer?.size?.ifHasValidLength { contentLength ->
                    breadcrumb.setData("response_body_size", contentLength)
                }
            }

            hint.set(TypeCheckHint.APOLLO_RESPONSE, httpResponse)
        }

        hub.addBreadcrumb(breadcrumb, hint)
    }

    // Extensions

    private fun HttpResponse.headersContentLength(): Long {
        return headers.firstOrNull { it.name == "Content-Length" }?.value?.toLongOrNull() ?: -1L
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
         * @param request the Apollo request object
         * @param response the Apollo response object
         */
        fun execute(span: ISpan, request: HttpRequest, response: HttpResponse?): ISpan?
    }

    companion object {
        const val SENTRY_APOLLO_3_VARIABLES = "SENTRY-APOLLO-3-VARIABLES"
        const val SENTRY_APOLLO_3_OPERATION_NAME = "SENTRY-APOLLO-3-OPERATION-NAME"
        const val SENTRY_APOLLO_3_OPERATION_TYPE = "SENTRY-APOLLO-3-OPERATION-TYPE"
    }
}
