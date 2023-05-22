package io.sentry.android.okhttp

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint
import io.sentry.android.okhttp.SentryOkHttpEventListener.Companion.CONNECTION_EVENT
import io.sentry.android.okhttp.SentryOkHttpEventListener.Companion.CONNECT_EVENT
import io.sentry.android.okhttp.SentryOkHttpEventListener.Companion.REQUEST_BODY_EVENT
import io.sentry.android.okhttp.SentryOkHttpEventListener.Companion.REQUEST_HEADERS_EVENT
import io.sentry.android.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_BODY_EVENT
import io.sentry.android.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_HEADERS_EVENT
import io.sentry.android.okhttp.SentryOkHttpEventListener.Companion.SECURE_CONNECT_EVENT
import io.sentry.util.UrlUtils
import okhttp3.Request
import okhttp3.Response

internal class SentryOkHttpEvent(private val hub: IHub, private val request: Request) {
    private val eventSpans: MutableMap<String, ISpan> = HashMap()
    private val breadcrumb: Breadcrumb
    internal val callRootSpan: ISpan?
    private var response: Response? = null

    init {
        val urlDetails = UrlUtils.parse(request.url.toString())
        val url = urlDetails.urlOrFallback
        val host: String = request.url.host
        val encodedPath: String = request.url.encodedPath
        val method: String = request.method

        // We start the call span that will contain all the others
        callRootSpan = hub.span?.startChild("http.client", "$method $url")

        urlDetails.applyToSpan(callRootSpan)

        // We setup a breadcrumb with all meaningful data
        breadcrumb = Breadcrumb.http(url, method)
        breadcrumb.setData("url", url)
        breadcrumb.setData("host", host)
        breadcrumb.setData("path", encodedPath)
        breadcrumb.setData("method", method)
        breadcrumb.setData("success", true)

        // We add the same data to the root call span
        callRootSpan?.setData("url", url)
        callRootSpan?.setData("host", host)
        callRootSpan?.setData("path", encodedPath)
        callRootSpan?.setData("http.method", method)
        callRootSpan?.setData("success", true)
    }

    /**
     * Sets the [Response] that will be sent in the breadcrumb [Hint].
     * Also, it sets the protocol and status code in the breadcrumb and the call root span.
     */
    fun setResponse(response: Response) {
        this.response = response
        breadcrumb.setData("protocol", response.protocol.name)
        breadcrumb.setData("status_code", response.code)
        callRootSpan?.setData("protocol", response.protocol.name)
        callRootSpan?.setData("status_code", response.code)
        callRootSpan?.status = SpanStatus.fromHttpStatusCode(response.code)
    }

    fun setProtocol(protocolName: String?) {
        if (protocolName != null) {
            breadcrumb.setData("protocol", protocolName)
            callRootSpan?.setData("protocol", protocolName)
        }
    }

    fun setRequestBodySize(byteCount: Long) {
        if (byteCount > -1) {
            breadcrumb.setData("request_content_length", byteCount)
            callRootSpan?.setData("http.request_content_length", byteCount)
        }
    }

    fun setResponseBodySize(byteCount: Long) {
        if (byteCount > -1) {
            breadcrumb.setData("response_content_length", byteCount)
            callRootSpan?.setData("http.response_content_length", byteCount)
        }
    }

    /**
     * Sets the success flag in the breadcrumb and the call root span to false.
     * Also sets the [errorMessage] if not null.
     */
    fun setError(errorMessage: String?) {
        breadcrumb.setData("success", false)
        callRootSpan?.setData("success", false)
        if (errorMessage != null) {
            breadcrumb.setData("error_message", errorMessage)
            callRootSpan?.setData("error_message", errorMessage)
        }
    }

    /** Starts a span, if the callRootSpan is not null. */
    fun startSpan(event: String) {
        // Find the parent of the span being created. E.g. secureConnect is child of connect
        val parentSpan = when (event) {
            // PROXY_SELECT, DNS, CONNECT and CONNECTION are not children of one another
            SECURE_CONNECT_EVENT -> eventSpans[CONNECT_EVENT]
            REQUEST_HEADERS_EVENT -> eventSpans[CONNECTION_EVENT]
            REQUEST_BODY_EVENT -> eventSpans[CONNECTION_EVENT]
            RESPONSE_HEADERS_EVENT -> eventSpans[CONNECTION_EVENT]
            RESPONSE_BODY_EVENT -> eventSpans[CONNECTION_EVENT]
            else -> callRootSpan
        } ?: callRootSpan
        val span = parentSpan?.startChild("http.client.details", event) ?: return
        eventSpans[event] = span
    }

    /** Finishes a previously started span, and runs [beforeFinish] on it and on the call root span. */
    fun finishSpan(event: String, beforeFinish: ((span: ISpan) -> Unit)? = null) {
        val span = eventSpans[event] ?: return
        beforeFinish?.invoke(span)
        callRootSpan?.let { beforeFinish?.invoke(it) }
        span.finish()
    }

    /** Finishes the call root span, and runs [beforeFinish] on it. Then a breadcrumb is sent. */
    fun finishEvent(beforeFinish: ((span: ISpan) -> Unit)? = null) {
        callRootSpan ?: return

        // We forcefully finish all spans, even if they should already have been finished through finishSpan()
        eventSpans.values.filter { !it.isFinished }.forEach { it.finish(SpanStatus.DEADLINE_EXCEEDED) }
        beforeFinish?.invoke(callRootSpan)
        callRootSpan.finish()

        // We put data in the hint and send a breadcrumb
        val hint = Hint()
        hint.set(TypeCheckHint.OKHTTP_REQUEST, request)
        response?.let { hint.set(TypeCheckHint.OKHTTP_RESPONSE, it) }

        hub.addBreadcrumb(breadcrumb, hint)
        return
    }
}
