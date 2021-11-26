package io.sentry.android.okhttp

import io.sentry.Breadcrumb
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SpanStatus
import io.sentry.TracingOrigins
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class SentryOkHttpInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null
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
            if (span != null && TracingOrigins.contain(hub.options.tracingOrigins, request.url.toString())) {
                span.toSentryTrace().let {
                    requestBuilder.addHeader(it.name, it.value)
                }
                span.toTraceStateHeader()?.let {
                    requestBuilder.addHeader(it.name, it.value)
                }
            }
            request = requestBuilder.build()
            response = chain.proceed(request)
            code = response.code
            span?.status = SpanStatus.fromHttpStatusCode(code)
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
            response?.body?.contentLength().ifHasValidLength {
                breadcrumb.setData("response_body_size", it)
            }
            hub.addBreadcrumb(breadcrumb)
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
