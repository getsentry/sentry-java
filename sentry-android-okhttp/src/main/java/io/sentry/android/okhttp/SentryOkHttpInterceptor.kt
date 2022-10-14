package io.sentry.android.okhttp

import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HttpStatusCodeRange
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryEvent
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint.OKHTTP_REQUEST
import io.sentry.TypeCheckHint.OKHTTP_RESPONSE
import io.sentry.exception.ExceptionMechanismException
import io.sentry.exception.SentryHttpClientException
import io.sentry.protocol.Mechanism
import io.sentry.util.HttpUtils
import io.sentry.util.PropagationTargetsUtils
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class SentryOkHttpInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null,
    // should this be under the options or here? also define the names
    private val captureFailedRequests: Boolean = false,
    private val failedRequestStatusCodes: List<HttpStatusCodeRange> = listOf(
        HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX)
    ),
    private val failedRequestTargets: List<String> = listOf(".*")
) : Interceptor {

    constructor() : this(HubAdapter.getInstance())
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
                PropagationTargetsUtils.contain(hub.options.tracePropagationTargets, request.url.toString())
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
            // breadcrumbs are added on the finally block because we'd like to know if the device
            // had an unstable connection or something similar
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

        if (!captureFailedRequests ||
            !PropagationTargetsUtils.contain(failedRequestTargets, requestUrl) ||
            !containsStatusCode(response.code)
        ) {
            return
        }

        val mechanism = Mechanism().apply {
            type = "SentryOkHttpInterceptor"
        }
        val exception = SentryHttpClientException(
            "Event was captured because the request status code was ${response.code}"
        )
        val mechanismException = ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
        val event = SentryEvent(mechanismException)

        val hint = Hint()
        hint.set("request", request)
        hint.set("response", response)

        // remove after fields indexed
//        val tags = mutableMapOf<String, String>()
//        tags["status_code"] = response.code.toString()
//        tags["url"] = requestUrl

        val unknownRequestFields = mutableMapOf<String, Any>()

        val sentryRequest = io.sentry.protocol.Request().apply {
            url = requestUrl
            // Cookie is only sent if isSendDefaultPii is enabled
            cookies = if (hub.options.isSendDefaultPii) request.headers["Cookie"] else null
            method = request.method
            queryString = query
            headers = getHeaders(request.headers)
            fragment = urlFragment

            request.body?.contentLength().ifHasValidLength {
                // should be mapped in relay and added to the protocol, right now
                // relay isn't retaining unmapped fields
                unknownRequestFields["body_size"] = it
            }

            unknown = unknownRequestFields.ifEmpty { null }
        }

        val sentryResponse = io.sentry.protocol.Response().apply {
            // Cookie is only sent if isSendDefaultPii is enabled due to PII
            cookies = if (hub.options.isSendDefaultPii) response.headers["Cookie"] else null
            headers = getHeaders(response.headers)
            statusCode = response.code

            response.body?.contentLength().ifHasValidLength {
                bodySize = it
            }
        }

//        event.tags = tags
        event.request = sentryRequest
        event.contexts.setResponse(sentryResponse)

        hub.captureEvent(event, hint)
    }

    private fun containsStatusCode(statusCode: Int): Boolean {
        for (item in failedRequestStatusCodes) {
            if (item.isInRange(statusCode)) {
                return true
            }
        }
        return false
    }

    private fun getHeaders(requestHeaders: Headers): MutableMap<String, String>? {
        // Headers are only sent if isSendDefaultPii is enabled due to PII
        if (!hub.options.isSendDefaultPii) {
            return null
        }

        val headers = mutableMapOf<String, String>()

        for (i in 0 until requestHeaders.size) {
            val name = requestHeaders.name(i)

            // header is only sent if isn't sensitive
            if (HttpUtils.containsSensitiveHeader(name)) {
                continue
            }

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
