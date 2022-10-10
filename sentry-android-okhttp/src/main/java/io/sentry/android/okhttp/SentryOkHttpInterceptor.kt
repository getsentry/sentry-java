package io.sentry.android.okhttp

import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SpanStatus
import io.sentry.TracePropagationTargets
import io.sentry.SentryEvent
import io.sentry.TypeCheckHint.OKHTTP_REQUEST
import io.sentry.TypeCheckHint.OKHTTP_RESPONSE
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class SentryOkHttpInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null,
    // TODO: should this be under the options or here? also define the names
    private val captureFailedRequests: Boolean = false,
    private val failedRequestStatusCode: List<StatusCodeRange> = listOf(StatusCodeRange(500, 599)),
    private val failedRequestsTargets: List<String> = listOf(".*")
) : Interceptor {

    constructor(hub: IHub) : this(hub, null)
    constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val url = request.url.toString()
        val method = request.method

        // read transaction from the bound scope
        val span = hub.span?.startChild("http.client", "$method $url")

        var response: Response? = null

        var code: Int? = null
        try {
            val requestBuilder = request.newBuilder()
            if (span != null &&
                TracePropagationTargets.contain(hub.options.tracePropagationTargets, request.url.toString())
            ) {
                span.toSentryTrace().let {
                    requestBuilder.addHeader(it.name, it.value)
                }

                span.toBaggageHeader(request.headers(BaggageHeader.BAGGAGE_HEADER))?.let {
                    requestBuilder.removeHeader(BaggageHeader.BAGGAGE_HEADER)
                    requestBuilder.addHeader(it.name, it.value)
                }
            }
            request = requestBuilder.build()
            response = chain.proceed(request)
            code = response.code
            span?.status = SpanStatus.fromHttpStatusCode(code)

            // OkHttp errors (4xx, 5xx) don't throw, so it's safe to call within this block.
            captureEvent(request, response)

            return response
        } catch (e: IOException) {
            span?.apply {
                this.throwable = e
                this.status = SpanStatus.INTERNAL_ERROR
            }
            throw e
        } finally {
            finishSpan(span, request, response)

            val breadcrumb = Breadcrumb.http(request.url.toString(), request.method, code)
            request.body?.contentLength().ifHasValidLength {
                breadcrumb.setData("request_body_size", it)
            }

            val hint = Hint()
                .also { it.set(OKHTTP_REQUEST, request) }
            response?.let {
                it.body?.contentLength().ifHasValidLength { responseBodySize ->
                    breadcrumb.setData("response_body_size", responseBodySize)
                }

                hint[OKHTTP_RESPONSE] = it
            }

            hub.addBreadcrumb(breadcrumb, hint)
        }
    }

    private fun finishSpan(span: ISpan?, request: Request, response: Response?) {
        if (span != null) {
            if (beforeSpan != null) {
                val result = beforeSpan.execute(span, request, response)
                if (result == null) {
                    // span is dropped
                    span.spanContext.sampled = false
                } else {
                    span.finish()
                }
            } else {
                span.finish()
            }
        }
    }

    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
        if (this != null && this != -1L) {
            fn.invoke(this)
        }
    }

    // TODO: ignore exceptions of the type UnknownHostException

    private fun captureEvent(request: Request, response: Response) {
        // not possible to get a parameterized url, but we remove at least the
        // query string and the fragment.
        // url example: https://api.github.com/users/getsentry/repos/#fragment?query=query
        // url will be: https://api.github.com/users/getsentry/repos/
        // ideally we'd like a parameterized url: https://api.github.com/users/{user}/repos/
        // but that's not possible
        var requestUrl = request.url.toString()

        val query = request.url.query
        if (!query.isNullOrEmpty()) {
            requestUrl = requestUrl.replace("?$query", "")
        }

        val urlFragment = request.url.fragment
        if (!urlFragment.isNullOrEmpty()) {
            requestUrl = requestUrl.replace("#$urlFragment", "")
        }

        if (!captureFailedRequests || !TracePropagationTargets.contain(failedRequestsTargets, requestUrl) || !containsStatusCode(response.code)) {
            return
        }

        val mechanism = Mechanism().apply {
            type = "SentryOkHttpInterceptor"

//            // with description, mechanism in the UI is buggy
//            description = message

            // TODO: should it be synthetic? likely not, mechanism SentryOkHttpInterceptor should be considered for grouping
//            synthetic = true
        }
        val exception = SentryHttpClientError("Event was captured because the request status code was ${response.code}")
        // TODO: okhttp thread uses thread pool so its always the same stack trace (okhttp frames only), stacktrace is set as snapshot=true, is that ok?
        val mechanismException = ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
        val event = SentryEvent(mechanismException)

        // TODO: Do we need a message if the request and response already have the info?
        val sentryMessage = Message().apply {
            message = "HTTP url: %s status: %s"
            params = listOf(requestUrl, response.code.toString())
        }

        val hint = Hint()
        hint.set("request", request)
        hint.set("response", response)

        // TODO: remove after fields indexed
        val tags = mutableMapOf<String, String>()
        tags["status_code"] = response.code.toString()
        tags["url"] = requestUrl

        val unknownRequestFields = mutableMapOf<String, Any>()

        val sentryRequest = io.sentry.protocol.Request().apply {
            url = requestUrl
            cookies = request.headers["Cookie"]
            method = request.method
            queryString = query
            headers = getHeaders(request.headers)
            fragment = urlFragment

            request.body?.contentLength().ifHasValidLength {
                // TODO: should be mapped in relay and added to the protocol
                unknownRequestFields["body_size"] = it
            }

            unknown = unknownRequestFields.ifEmpty { null }
        }

        val sentryResponse = io.sentry.protocol.Response().apply {
            cookies = response.headers["Cookie"]
            headers = getHeaders(response.headers)
            statusCode = response.code

            response.body?.contentLength().ifHasValidLength {
                bodySize = it
            }
        }

        event.tags = tags
        event.request = sentryRequest
        event.contexts.setResponse(sentryResponse)
        event.message = sentryMessage

        hub.captureEvent(event, hint)
    }

    private fun containsStatusCode(statusCode: Int): Boolean {
        for(item in failedRequestStatusCode) {
            if (item.isInRange(statusCode)) {
                return true
            }
        }
        return false
    }

    private fun getHeaders(requestHeaders: Headers): MutableMap<String, String>? {
        // TODO: should it be under isSendDefaultPii?
        // Server already does data scrubbing
        if (!hub.options.isSendDefaultPii) {
            return null
        }

        val headers = mutableMapOf<String, String>()

        for (i in 0 until requestHeaders.size) {
            // TODO: should we remove sentry-trace and baggage from headers?
            val name = requestHeaders.name(i)
            val value = requestHeaders.value(i)
            headers[name] = value
        }
        return headers
    }

    /**
     * The BeforeSpan callback
     */
    fun interface BeforeSpanCallback {
        /**
         * Mutates or drops span before being added
         *
         * @param span the span to mutate or drop
         * @param request the HTTP request executed by okHttp
         * @param response the HTTP response received by okHttp
         */
        fun execute(span: ISpan, request: Request, response: Response?): ISpan?
    }
}
