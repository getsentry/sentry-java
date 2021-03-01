package io.sentry.samples.android

import io.sentry.Sentry
import io.sentry.SpanStatus
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

class NetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()

        // read transaction from the bound scope
        val span = Sentry.getSpan()?.startChild("http.client", request.url().toString())

        val response: Response
        var code = 500
        try {
            response = chain.proceed(request)
            code = response.code()
        } catch (e: IOException) {
            span?.throwable = e
            throw e
        } finally {
            span?.finish(SpanStatus.fromHttpStatusCode(code, SpanStatus.INTERNAL_ERROR))
        }

        return response
    }
}
