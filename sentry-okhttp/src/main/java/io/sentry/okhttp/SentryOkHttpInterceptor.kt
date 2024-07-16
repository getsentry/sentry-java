package io.sentry.okhttp

import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HttpStatusCodeRange
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint.OKHTTP_REQUEST
import io.sentry.TypeCheckHint.OKHTTP_RESPONSE
import io.sentry.okhttp.SentryOkHttpInterceptor.BeforeSpanCallback
import io.sentry.transport.CurrentDateProvider
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.Platform
import io.sentry.util.PropagationTargetsUtils
import io.sentry.util.TracingUtils
import io.sentry.util.UrlUtils
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * The Sentry's [SentryOkHttpInterceptor], it will automatically add a breadcrumb and start a span
 * out of the active span bound to the scope for each HTTP Request.
 * If [captureFailedRequests] is enabled, the SDK will capture HTTP Client errors as well.
 *
 * @param hub The [IHub], internal and only used for testing.
 * @param beforeSpan The [ISpan] can be customized or dropped with the [BeforeSpanCallback].
 * @param captureFailedRequests The SDK will only capture HTTP Client errors if it is enabled,
 * Defaults to false.
 * @param failedRequestStatusCodes The SDK will only capture HTTP Client errors if the HTTP Response
 * status code is within the defined ranges.
 * @param failedRequestTargets The SDK will only capture HTTP Client errors if the HTTP Request URL
 * is a match for any of the defined targets.
 */
public open class SentryOkHttpInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null,
    private val captureFailedRequests: Boolean = true,
    private val failedRequestStatusCodes: List<HttpStatusCodeRange> = listOf(
        HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX)
    ),
    private val failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS)
) : Interceptor {

    public constructor() : this(HubAdapter.getInstance())
    public constructor(hub: IHub) : this(hub, null)
    public constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)

    init {
        addIntegrationToSdkVersion(javaClass)
        SentryIntegrationPackageStorage.getInstance()
            .addPackage("maven:io.sentry:sentry-okhttp", BuildConfig.VERSION_NAME)
    }

    @Suppress("LongMethod")
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val urlDetails = UrlUtils.parse(request.url.toString())
        val url = urlDetails.urlOrFallback
        val method = request.method

        val span: ISpan?
        val okHttpEvent: SentryOkHttpEvent?

        if (SentryOkHttpEventListener.eventMap.containsKey(chain.call())) {
            // read the span from the event listener
            okHttpEvent = SentryOkHttpEventListener.eventMap[chain.call()]
            span = okHttpEvent?.callRootSpan
        } else {
            // read the span from the bound scope
            okHttpEvent = null
            val parentSpan = if (Platform.isAndroid()) hub.transaction else hub.span
            span = parentSpan?.startChild("http.client", "$method $url")
        }
        val startTimestamp = CurrentDateProvider.getInstance().currentTimeMillis

        span?.spanContext?.origin = TRACE_ORIGIN

        urlDetails.applyToSpan(span)

        val isFromEventListener = okHttpEvent != null
        var response: Response? = null
        var code: Int? = null

        try {
            val requestBuilder = request.newBuilder()

            TracingUtils.traceIfAllowed(
                hub,
                request.url.toString(),
                request.headers(BaggageHeader.BAGGAGE_HEADER),
                span
            )?.let { tracingHeaders ->
                requestBuilder.addHeader(tracingHeaders.sentryTraceHeader.name, tracingHeaders.sentryTraceHeader.value)
                tracingHeaders.baggageHeader?.let {
                    requestBuilder.removeHeader(BaggageHeader.BAGGAGE_HEADER)
                    requestBuilder.addHeader(it.name, it.value)
                }
            }

            request = requestBuilder.build()
            response = chain.proceed(request)
            code = response.code
            span?.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, code)
            span?.status = SpanStatus.fromHttpStatusCode(code)

            // OkHttp errors (4xx, 5xx) don't throw, so it's safe to call within this block.
            // breadcrumbs are added on the finally block because we'd like to know if the device
            // had an unstable connection or something similar
            if (shouldCaptureClientError(request, response)) {
                // If we capture the client error directly, it could be associated with the
                // currently running span by the backend. In case the listener is in use, that is
                // an inner span. So, if the listener is in use, we let it capture the client
                // error, to shown it in the http root call span in the dashboard.
                if (isFromEventListener && okHttpEvent != null) {
                    okHttpEvent.setClientErrorResponse(response)
                } else {
                    SentryOkHttpUtils.captureClientError(hub, request, response)
                }
            }

            return response
        } catch (e: IOException) {
            span?.apply {
                this.throwable = e
                this.status = SpanStatus.INTERNAL_ERROR
            }
            throw e
        } finally {
            finishSpan(span, request, response, isFromEventListener)

            // The SentryOkHttpEventListener will send the breadcrumb itself if used for this call
            if (!isFromEventListener) {
                sendBreadcrumb(request, code, response, startTimestamp)
            }
        }
    }

    private fun sendBreadcrumb(
        request: Request,
        code: Int?,
        response: Response?,
        startTimestamp: Long
    ) {
        val breadcrumb = Breadcrumb.http(request.url.toString(), request.method, code)
        request.body?.contentLength().ifHasValidLength {
            breadcrumb.setData("http.request_content_length", it)
        }

        val hint = Hint().also { it.set(OKHTTP_REQUEST, request) }
        response?.let {
            it.body?.contentLength().ifHasValidLength { responseBodySize ->
                breadcrumb.setData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY, responseBodySize)
            }

            hint[OKHTTP_RESPONSE] = it
        }
        // needs this as unix timestamp for rrweb
        breadcrumb.setData(SpanDataConvention.HTTP_START_TIMESTAMP, startTimestamp)
        breadcrumb.setData(SpanDataConvention.HTTP_END_TIMESTAMP, CurrentDateProvider.getInstance().currentTimeMillis)

        hub.addBreadcrumb(breadcrumb, hint)
    }

    private fun finishSpan(span: ISpan?, request: Request, response: Response?, isFromEventListener: Boolean) {
        if (span == null) {
            return
        }
        if (beforeSpan != null) {
            val result = beforeSpan.execute(span, request, response)
            if (result == null) {
                // span is dropped
                span.spanContext.sampled = false
            }
            // The SentryOkHttpEventListener will finish the span itself if used for this call
            if (!isFromEventListener) {
                span.finish()
            }
        } else {
            // The SentryOkHttpEventListener will finish the span itself if used for this call
            if (!isFromEventListener) {
                span.finish()
            }
        }
    }

    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
        if (this != null && this != -1L) {
            fn.invoke(this)
        }
    }

    private fun shouldCaptureClientError(request: Request, response: Response): Boolean {
        // return if the feature is disabled or its not within the range
        if (!captureFailedRequests || !containsStatusCode(response.code)) {
            return false
        }

        // return if its not a target match
        if (!PropagationTargetsUtils.contain(failedRequestTargets, request.url.toString())) {
            return false
        }

        return true
    }

    private fun containsStatusCode(statusCode: Int): Boolean {
        for (item in failedRequestStatusCodes) {
            if (item.isInRange(statusCode)) {
                return true
            }
        }
        return false
    }

    /**
     * The BeforeSpan callback
     */
    public fun interface BeforeSpanCallback {
        /**
         * Mutates or drops span before being added
         *
         * @param span the span to mutate or drop
         * @param request the HTTP request executed by okHttp
         * @param response the HTTP response received by okHttp
         */
        public fun execute(span: ISpan, request: Request, response: Response?): ISpan?
    }
}
