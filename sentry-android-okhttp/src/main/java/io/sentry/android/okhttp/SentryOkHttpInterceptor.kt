package io.sentry.android.okhttp

import io.sentry.HttpStatusCodeRange
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.android.okhttp.SentryOkHttpInterceptor.BeforeSpanCallback
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * The Sentry's [SentryOkHttpInterceptor], it will automatically add a breadcrumb and start a span
 * out of the active span bound to the scope for each HTTP Request.
 * If [captureFailedRequests] is enabled, the SDK will capture HTTP Client errors as well.
 *
 * @param hub The [IHub], internal and only used for testing.
 * @param beforeSpan The [ISpan] can be customized or dropped with the [BeforeSpanCallback].
 * @param captureFailedRequests The SDK will only capture HTTP Client errors if it is enabled,
 * Defaults to true.
 * @param failedRequestStatusCodes The SDK will only capture HTTP Client errors if the HTTP Response
 * status code is within the defined ranges.
 * @param failedRequestTargets The SDK will only capture HTTP Client errors if the HTTP Request URL
 * is a match for any of the defined targets.
 */
@Deprecated(
    "Use SentryOkHttpInterceptor from sentry-okhttp instead",
    ReplaceWith("SentryOkHttpInterceptor", "io.sentry.okhttp.SentryOkHttpInterceptor")
)
class SentryOkHttpInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null,
    private val captureFailedRequests: Boolean = true,
    private val failedRequestStatusCodes: List<HttpStatusCodeRange> = listOf(
        HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX)
    ),
    private val failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS)
) : Interceptor by io.sentry.okhttp.SentryOkHttpInterceptor(
    hub,
    { span, request, response ->
        beforeSpan ?: return@SentryOkHttpInterceptor span
        beforeSpan.execute(span, request, response)
    },
    captureFailedRequests,
    failedRequestStatusCodes,
    failedRequestTargets
) {

    constructor() : this(HubAdapter.getInstance())
    constructor(hub: IHub) : this(hub, null)
    constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)

    init {
        addIntegrationToSdkVersion("OkHttp")
        SentryIntegrationPackageStorage.getInstance()
            .addPackage("maven:io.sentry:sentry-android-okhttp", BuildConfig.VERSION_NAME)
    }

    /**
     * The BeforeSpan callback
     */
    @Deprecated(
        "Use BeforeSpanCallback from sentry-okhttp instead",
        ReplaceWith("BeforeSpanCallback", "io.sentry.okhttp.SentryOkHttpInterceptor.BeforeSpanCallback")
    )
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
