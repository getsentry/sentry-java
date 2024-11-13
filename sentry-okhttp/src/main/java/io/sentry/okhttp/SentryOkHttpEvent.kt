package io.sentry.okhttp

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryDate
import io.sentry.SentryLevel
import io.sentry.SpanDataConvention
import io.sentry.TypeCheckHint
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.CONNECTION_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.CONNECT_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.REQUEST_BODY_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.REQUEST_HEADERS_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_BODY_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_HEADERS_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.SECURE_CONNECT_EVENT
import io.sentry.transport.CurrentDateProvider
import io.sentry.util.Platform
import io.sentry.util.UrlUtils
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

private const val PROTOCOL_KEY = "protocol"
private const val ERROR_MESSAGE_KEY = "error_message"
private const val RESPONSE_BODY_TIMEOUT_MILLIS = 800L
internal const val TRACE_ORIGIN = "auto.http.okhttp"

@Suppress("TooManyFunctions")
internal class SentryOkHttpEvent(private val hub: IHub, private val request: Request) {
    private val eventSpans: MutableMap<String, ISpan> = ConcurrentHashMap()
    private val breadcrumb: Breadcrumb
    internal val callRootSpan: ISpan?
    private var response: Response? = null
    private var clientErrorResponse: Response? = null
    private val isReadingResponseBody = AtomicBoolean(false)
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
        val parentSpan = if (Platform.isAndroid()) hub.transaction else hub.span
        callRootSpan = parentSpan?.startChild("http.client", "$method $url")
        callRootSpan?.spanContext?.origin = TRACE_ORIGIN
        urlDetails.applyToSpan(callRootSpan)

        // We setup a breadcrumb with all meaningful data
        breadcrumb = Breadcrumb.http(url, method)
        breadcrumb.setData("host", host)
        breadcrumb.setData("path", encodedPath)
        // needs this as unix timestamp for rrweb
        breadcrumb.setData(SpanDataConvention.HTTP_START_TIMESTAMP, CurrentDateProvider.getInstance().currentTimeMillis)

        // We add the same data to the root call span
        callRootSpan?.setData("url", url)
        callRootSpan?.setData("host", host)
        callRootSpan?.setData("path", encodedPath)
        callRootSpan?.setData(SpanDataConvention.HTTP_METHOD_KEY, method.uppercase())
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
        // Find the parent of the span being created. E.g. secureConnect is child of connect
        val parentSpan = findParentSpan(event)
        val span = parentSpan?.startChild("http.client.$event", "$method $url") ?: return
        if (event == RESPONSE_BODY_EVENT) {
            // We save this event is reading the response body, so that it will not be auto-finished
            isReadingResponseBody.set(true)
        }
        span.spanContext.origin = TRACE_ORIGIN
        eventSpans[event] = span
    }

    /** Finishes a previously started span, and runs [beforeFinish] on it, on its parent and on the call root span. */
    fun finishSpan(event: String, beforeFinish: ((span: ISpan) -> Unit)? = null): ISpan? {
        val span = eventSpans[event] ?: return null
        val parentSpan = findParentSpan(event)
        beforeFinish?.invoke(span)
        moveThrowableToRootSpan(span)
        if (parentSpan != null && parentSpan != callRootSpan) {
            beforeFinish?.invoke(parentSpan)
            moveThrowableToRootSpan(parentSpan)
        }
        callRootSpan?.let { beforeFinish?.invoke(it) }
        span.finish()
        return span
    }

    /** Finishes the call root span, and runs [beforeFinish] on it. Then a breadcrumb is sent. */
    fun finishEvent(finishDate: SentryDate? = null, beforeFinish: ((span: ISpan) -> Unit)? = null) {
        // If the event already finished, we don't do anything
        if (isEventFinished.getAndSet(true)) {
            return
        }
        // We put data in the hint and send a breadcrumb
        val hint = Hint()
        hint.set(TypeCheckHint.OKHTTP_REQUEST, request)
        response?.let { hint.set(TypeCheckHint.OKHTTP_RESPONSE, it) }

        // needs this as unix timestamp for rrweb
        breadcrumb.setData(SpanDataConvention.HTTP_END_TIMESTAMP, CurrentDateProvider.getInstance().currentTimeMillis)
        // We send the breadcrumb even without spans.
        hub.addBreadcrumb(breadcrumb, hint)

        // No span is created (e.g. no transaction is running)
        if (callRootSpan == null) {
            // We report the client error even without spans.
            clientErrorResponse?.let {
                SentryOkHttpUtils.captureClientError(hub, it.request, it)
            }
            return
        }

        // We forcefully finish all spans, even if they should already have been finished through finishSpan()
        eventSpans.values.filter { !it.isFinished }.forEach {
            moveThrowableToRootSpan(it)
            if (finishDate != null) {
                it.finish(it.status, finishDate)
            } else {
                it.finish()
            }
        }
        beforeFinish?.invoke(callRootSpan)
        // We report the client error here, after all sub-spans finished, so that it will be bound
        // to the root call span.
        clientErrorResponse?.let {
            SentryOkHttpUtils.captureClientError(hub, it.request, it)
        }
        if (finishDate != null) {
            callRootSpan.finish(callRootSpan.status, finishDate)
        } else {
            callRootSpan.finish()
        }
        return
    }

    /** Move any throwable from an inner span to the call root span. */
    private fun moveThrowableToRootSpan(span: ISpan) {
        if (span != callRootSpan && span.throwable != null && span.status != null) {
            callRootSpan?.throwable = span.throwable
            callRootSpan?.status = span.status
            span.throwable = null
        }
    }

    private fun findParentSpan(event: String): ISpan? = when (event) {
        // PROXY_SELECT, DNS, CONNECT and CONNECTION are not children of one another
        SECURE_CONNECT_EVENT -> eventSpans[CONNECT_EVENT]
        REQUEST_HEADERS_EVENT -> eventSpans[CONNECTION_EVENT]
        REQUEST_BODY_EVENT -> eventSpans[CONNECTION_EVENT]
        RESPONSE_HEADERS_EVENT -> eventSpans[CONNECTION_EVENT]
        RESPONSE_BODY_EVENT -> eventSpans[CONNECTION_EVENT]
        else -> callRootSpan
    } ?: callRootSpan

    fun scheduleFinish(timestamp: SentryDate) {
        try {
            hub.options.executorService.schedule({
                if (!isReadingResponseBody.get() &&
                    (eventSpans.values.all { it.isFinished } || callRootSpan?.isFinished != true)
                ) {
                    finishEvent(timestamp)
                }
            }, RESPONSE_BODY_TIMEOUT_MILLIS)
        } catch (e: RejectedExecutionException) {
            hub.options
                .logger
                .log(
                    SentryLevel.ERROR,
                    "Failed to call the executor. OkHttp span will not be finished " +
                        "automatically. Did you call Sentry.close()?",
                    e
                )
        }
    }
}
