package io.sentry.android.okhttp

import io.sentry.Breadcrumb
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.Sentry
//import io.sentry.SentryLevel
import io.sentry.SpanStatus
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

class SentryOkHttpInterceptor(
// Do we need min levels?
//        val minEventLevel: SentryLevel = SentryLevel.ERROR,
//        val minBreadcrumbLevel: SentryLevel = SentryLevel.INFO,
        val hub: IHub = HubAdapter.getInstance(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // read transaction from the bound scope
        val span = Sentry.getSpan()?.startChild("http.client", request.url.toString())

        val response: Response
        var code = -1
        try {
            response = chain.proceed(request)
            code = response.code
        } catch (e: IOException) {
            span?.throwable = e
            throw e
        } finally {
            span?.finish(SpanStatus.fromHttpStatusCode(code, SpanStatus.INTERNAL_ERROR))
        }
        addBreadcrumb("", "", 1)

        return response
    }

    // TODO: add package to options? how do we get options?
    // sdkVersion?.addPackage("maven:io.sentry:sentry-android-timber", BuildConfig.VERSION_NAME)

    private fun addBreadcrumb(url: String, method: String, code: Int) {
        val crumb = Breadcrumb.http(url, method, code)
        hub.addBreadcrumb(crumb)
    }
}
