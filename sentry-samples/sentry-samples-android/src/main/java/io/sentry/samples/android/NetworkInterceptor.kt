package io.sentry.samples.android

import io.sentry.Sentry
import io.sentry.SpanStatus
import okhttp3.Interceptor
import okhttp3.Response

class NetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // read transaction from the bound scope
        val span = Sentry.getSpan()?.startChild("http.client", request.url().toString())

        val response = chain.proceed(request)

        if (response.isSuccessful) {
            span?.finish(SpanStatus.OK)
        } else {
            span?.finish(SpanStatus.fromHttpStatusCode(response.code(), SpanStatus.INTERNAL_ERROR))
        }

        return response
    }
}