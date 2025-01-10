package io.sentry.okhttp

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryDate
import io.sentry.SpanDataConvention
import io.sentry.TypeCheckHint
import io.sentry.transport.CurrentDateProvider
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
    private val breadcrumb: Breadcrumb
    internal val callSpan: ISpan?
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
        callSpan = parentSpan?.startChild("http.client", "$method $url")
        callSpan?.spanContext?.origin = TRACE_ORIGIN
        urlDetails.applyToSpan(callSpan)

        // We setup a breadcrumb with all meaningful data
        breadcrumb = Breadcrumb.http(url, method)
        breadcrumb.setData("host", host)
        breadcrumb.setData("path", encodedPath)
        // needs this as unix timestamp for rrweb
        breadcrumb.setData(SpanDataConvention.HTTP_START_TIMESTAMP, CurrentDateProvider.getInstance().currentTimeMillis)

        // We add the same data to the call span
        callSpan?.setData("url", url)
        callSpan?.setData("host", host)
        callSpan?.setData("path", encodedPath)
        callSpan?.setData(SpanDataConvention.HTTP_METHOD_KEY, method.uppercase(Locale.ROOT))
    }

    /**
     * Sets the [Response] that will be sent in the breadcrumb [Hint].
     * Also, it sets the protocol and status code in the breadcrumb and the call span.
     */
    fun setResponse(response: Response) {
        this.response = response
        breadcrumb.setData(PROTOCOL_KEY, response.protocol.name)
        breadcrumb.setData("status_code", response.code)
        callSpan?.setData(PROTOCOL_KEY, response.protocol.name)
        callSpan?.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, response.code)
    }

    fun setProtocol(protocolName: String?) {
        if (protocolName != null) {
            breadcrumb.setData(PROTOCOL_KEY, protocolName)
            callSpan?.setData(PROTOCOL_KEY, protocolName)
        }
    }

    fun setRequestBodySize(byteCount: Long) {
        if (byteCount > -1) {
            breadcrumb.setData("request_content_length", byteCount)
            callSpan?.setData("http.request_content_length", byteCount)
        }
    }

    fun setResponseBodySize(byteCount: Long) {
        if (byteCount > -1) {
            breadcrumb.setData("response_content_length", byteCount)
            callSpan?.setData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY, byteCount)
        }
    }

    fun setClientErrorResponse(response: Response) {
        this.clientErrorResponse = response
    }

    /** Sets the [errorMessage] if not null. */
    fun setError(errorMessage: String?) {
        if (errorMessage != null) {
            breadcrumb.setData(ERROR_MESSAGE_KEY, errorMessage)
            callSpan?.setData(ERROR_MESSAGE_KEY, errorMessage)
        }
    }

    /** Record event start if the callRootSpan is not null. */
    fun onEventStart(event: String) {
        callSpan ?: return
        eventDates[event] = scopes.options.dateProvider.now()
    }

    /** Record event finish and runs [beforeFinish] on the call span. */
    fun onEventFinish(event: String, beforeFinish: ((span: ISpan) -> Unit)? = null) {
        val eventDate = eventDates.remove(event) ?: return
        callSpan ?: return
        beforeFinish?.invoke(callSpan)
        val eventDurationNanos = scopes.options.dateProvider.now().diff(eventDate)
        callSpan.setData(event, TimeUnit.NANOSECONDS.toMillis(eventDurationNanos))
    }

    /** Finishes the call span, and runs [beforeFinish] on it. Then a breadcrumb is sent. */
    fun finish(beforeFinish: ((span: ISpan) -> Unit)? = null) {
        // If the event already finished, we don't do anything
        if (isEventFinished.getAndSet(true)) {
            return
        }
        // We clear any date left, in case an event started, but never finished. Shouldn't happen.
        eventDates.clear()
        // We put data in the hint and send a breadcrumb
        val hint = Hint()
        hint.set(TypeCheckHint.OKHTTP_REQUEST, request)
        response?.let { hint.set(TypeCheckHint.OKHTTP_RESPONSE, it) }

        // needs this as unix timestamp for rrweb
        breadcrumb.setData(SpanDataConvention.HTTP_END_TIMESTAMP, CurrentDateProvider.getInstance().currentTimeMillis)
        // We send the breadcrumb even without spans.
        scopes.addBreadcrumb(breadcrumb, hint)

        callSpan?.let { beforeFinish?.invoke(it) }
        // We report the client error here so that it will be bound to the call span. We send it even if there is no running span.
        clientErrorResponse?.let {
            SentryOkHttpUtils.captureClientError(scopes, it.request, it)
        }
        callSpan?.finish()
        return
    }
}
