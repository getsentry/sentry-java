package io.sentry.android.okhttp

import io.sentry.Breadcrumb
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.Sentry
//import io.sentry.SentryLevel
import io.sentry.SpanStatus
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response

class SentryOkHttpInterceptor(
// Do we need min levels?
//        val minEventLevel: SentryLevel = SentryLevel.ERROR,
//        val minBreadcrumbLevel: SentryLevel = SentryLevel.INFO,
        private val hub: IHub = HubAdapter.getInstance(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val url = request.url.toString()
        val method = request.method

        // read transaction from the bound scope
        val activeSpan = Sentry.getSpan()

        val span = activeSpan?.startChild("http.client", url)
        span?.description = "$method $url"

        val traceHeader = span?.toSentryTrace()

        val response: Response
        val requestBody: RequestBody?

        var code = -1
        try {
            traceHeader?.let {
                request = request.newBuilder().addHeader(it.name, it.value).build()
            }

            requestBody = request.body
            response = chain.proceed(request)
            code = response.code
        } catch (e: IOException) {
            span?.throwable = e
            throw e
        } finally {
            span?.finish(SpanStatus.fromHttpStatusCode(code, SpanStatus.INTERNAL_ERROR))
        }

        // TODO: should we have a different interceptor for that?
        addBreadcrumb(url, method, code, requestBody?.contentLength(), response.body?.contentLength())

        // TODO: collect ideas
        // https://github.com/square/okhttp/blob/master/okhttp-logging-interceptor/src/main/kotlin/okhttp3/logging/HttpLoggingInterceptor.kt

        return response
    }

    // TODO: add package to options? how do we get options? does it even make sense?
    // sdkVersion?.addPackage("maven:io.sentry:sentry-android-timber", BuildConfig.VERSION_NAME)

    private fun addBreadcrumb(url: String, method: String, code: Int, bodyLength: Long?, requestLength: Long?) {
        val crumb = Breadcrumb.http(url, method, code)
        bodyLength?.let {
            crumb.setData("bodyLength", it)
        }
        requestLength?.let {
            crumb.setData("requestLength", it)
        }
        hub.addBreadcrumb(crumb)
    }
}
