package io.sentry.apollo3

import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.TypeCheckHint

class SentryApollo3HttpInterceptor(private val hub: IHub = HubAdapter.getInstance()) :
    HttpInterceptor {
    override suspend fun intercept(
        request: HttpRequest,
        chain: HttpInterceptorChain
    ): HttpResponse {
        val httpResponse = chain.proceed(request)

        val breadcrumb = Breadcrumb.http(request.url, request.method.name, httpResponse.statusCode)

        request.body?.contentLength.ifHasValidLength { contentLength ->
            breadcrumb.setData("request_body_size", contentLength)
        }

        httpResponse.headersContentLength().ifHasValidLength { contentLength ->
            breadcrumb.setData("response_body_size", contentLength)
        }

        val hint = Hint().also {
            it.set(TypeCheckHint.APOLLO_REQUEST, request)
            it.set(TypeCheckHint.APOLLO_RESPONSE, httpResponse)
        }

        hub.addBreadcrumb(breadcrumb, hint)

        return httpResponse
    }

    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
        if (this != null && this != -1L) {
            fn.invoke(this)
        }
    }
}
