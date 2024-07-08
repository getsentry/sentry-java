package io.sentry.okhttp

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryDate
import io.sentry.SpanDataConvention
import io.sentry.TypeCheckHint
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.CONNECTION_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.CONNECT_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.REQUEST_BODY_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.REQUEST_HEADERS_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_BODY_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_HEADERS_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.SECURE_CONNECT_EVENT
import io.sentry.util.Platform
import io.sentry.util.UrlUtils
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val PROTOCOL_KEY = "protocol"
private const val ERROR_MESSAGE_KEY = "error_message"
internal const val TRACE_ORIGIN = "auto.http.okhttp"

@Suppress("TooManyFunctions")
internal class SentryOkHttpEvent(private val scopes: IScopes, private val request: Request) {
    private val eventDates: MutableMap<String, SentryDate> = ConcurrentHashMap()
    private val eventSubSpansNanos: MutableMap<String, Long> = ConcurrentHashMap()
    private val breadcrumb: Breadcrumb
    internal val callRootSpan: ISpan?
    private var response: Response? = null
    private var clientErrorResponse: Response? = null
    private val isEventFinished = AtomicBoolean(false)
    private val url: String
    private val method: String

    init {
        val urlDetails = UrlUtils.parse(request.url.toString())
        url = urlDetails.urlOrFallback
        val host: String = request.url.host
        val encodedPath: String = request.url.encodedPath
        method = request.method

        // We start the call span that will contain all the others
        val parentSpan = if (Platform.isAndroid()) scopes.transaction else scopes.span
        callRootSpan = parentSpan?.startChild("http.client", "$method $url")
        callRootSpan?.spanContext?.origin = TRACE_ORIGIN
        urlDetails.applyToSpan(callRootSpan)

        // We setup a breadcrumb with all meaningful data
        breadcrumb = Breadcrumb.http(url, method)
        breadcrumb.setData("host", host)
        breadcrumb.setData("path", encodedPath)

        // We add the same data to the root call span
        callRootSpan?.setData("url", url)
        callRootSpan?.setData("host", host)
        callRootSpan?.setData("path", encodedPath)
        callRootSpan?.setData(SpanDataConvention.HTTP_METHOD_KEY, method.uppercase(Locale.ROOT))
    }

    /**
     * Sets the [Response] that will be sent in the breadcrumb [Hint].
     * Also, it sets the protocol and status code in the breadcrumb and the call root span.
     */
    fun setResponse(response: Response) {
        this.response = response
        breadcrumb.setData(PROTOCOL_KEY, response.protocol.name)
        breadcrumb.setData("status_code", response.code)
        callRootSpan?.setData(PROTOCOL_KEY, response.protocol.name)
        callRootSpan?.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, response.code)
    }

    fun setProtocol(protocolName: String?) {
        if (protocolName != null) {
            breadcrumb.setData(PROTOCOL_KEY, protocolName)
            callRootSpan?.setData(PROTOCOL_KEY, protocolName)
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
            callRootSpan?.setData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY, byteCount)
        }
    }

    fun setClientErrorResponse(response: Response) {
        this.clientErrorResponse = response
    }

    /** Sets the [errorMessage] if not null. */
    fun setError(errorMessage: String?) {
        if (errorMessage != null) {
            breadcrumb.setData(ERROR_MESSAGE_KEY, errorMessage)
            callRootSpan?.setData(ERROR_MESSAGE_KEY, errorMessage)
        }
    }

    /** Starts a span, if the callRootSpan is not null. */
    fun startSpan(event: String) {
        callRootSpan ?: return
        eventDates[event] = scopes.options.dateProvider.now()
    }

    /** Finishes a previously started span, and runs [beforeFinish] on it, on its parent and on the call root span. */
    fun finishSpan(event: String, beforeFinish: ((span: ISpan) -> Unit)? = null) {
        callRootSpan ?: return
        beforeFinish?.invoke(callRootSpan)
        eventDates.remove(event)?.let { date ->
            val subSpansNanos = eventSubSpansNanos[event] ?: 0
            val eventDurationNanos = scopes.options.dateProvider.now().diff(date) - subSpansNanos
            val parentEvent = findParentEvent(event)
            parentEvent?.let { eventSubSpansNanos[it] = (eventSubSpansNanos[it] ?: 0) + eventDurationNanos }
            callRootSpan.setData(event, TimeUnit.NANOSECONDS.toMillis(eventDurationNanos))
        }
    }

    /** Finishes the call root span, and runs [beforeFinish] on it. Then a breadcrumb is sent. */
    fun finishEvent(beforeFinish: ((span: ISpan) -> Unit)? = null) {
        eventDates.keys.forEach {
            finishSpan(it)
        }
        eventSubSpansNanos.clear()
        eventDates.clear()
        // If the event already finished, we don't do anything
        if (isEventFinished.getAndSet(true)) {
            return
        }
        // We put data in the hint and send a breadcrumb
        val hint = Hint()
        hint.set(TypeCheckHint.OKHTTP_REQUEST, request)
        response?.let { hint.set(TypeCheckHint.OKHTTP_RESPONSE, it) }

        // We send the breadcrumb even without spans.
        scopes.addBreadcrumb(breadcrumb, hint)

        callRootSpan?.let { beforeFinish?.invoke(it) }
        // We report the client error here so that it will be bound to the root call span. We send it even if there is no running span.
        clientErrorResponse?.let {
            SentryOkHttpUtils.captureClientError(scopes, it.request, it)
        }
        callRootSpan?.finish()
        return
    }

    private fun findParentEvent(event: String): String? = when (event) {
        // PROXY_SELECT, DNS, CONNECT and CONNECTION are not children of one another
        SECURE_CONNECT_EVENT -> CONNECT_EVENT
        REQUEST_HEADERS_EVENT -> CONNECTION_EVENT
        REQUEST_BODY_EVENT -> CONNECTION_EVENT
        RESPONSE_HEADERS_EVENT -> CONNECTION_EVENT
        RESPONSE_BODY_EVENT -> CONNECTION_EVENT
        else -> null
    }
}
