package io.sentry.android.okhttp

import io.sentry.Breadcrumb
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SpanStatus
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class SentryOkHttpInterceptor(
    private val hub: IHub = HubAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null
) : Interceptor {

    constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val url = request.url.toString()
        val method = request.method

        // read transaction from the bound scope
        var span = hub.span?.startChild("http.client", "$method $url")

        var response: Response? = null

        var code: Int? = null
        try {
            span?.toSentryTrace()?.let {
                request = request.newBuilder().addHeader(it.name, it.value).build()
            }
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
            if (span != null) {
                if (beforeSpan != null) {
                    span = beforeSpan.execute(span, request, response)
                }
                span?.finish()
            }
            val breadcrumb = Breadcrumb.http(request.url.toString(), request.method, code)
            request.body?.contentLength().ifHasValidLength {
                breadcrumb.setData("requestBodySize", it)
            }
            response?.body?.contentLength().ifHasValidLength {
                breadcrumb.setData("responseBodySize", it)
            }
            hub.addBreadcrumb(breadcrumb)
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
    interface BeforeSpanCallback {
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
