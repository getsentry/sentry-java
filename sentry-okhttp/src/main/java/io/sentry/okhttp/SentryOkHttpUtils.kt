package io.sentry.okhttp

import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryEvent
import io.sentry.TypeCheckHint
import io.sentry.exception.ExceptionMechanismException
import io.sentry.exception.SentryHttpClientException
import io.sentry.protocol.Mechanism
import io.sentry.util.HttpUtils
import io.sentry.util.UrlUtils
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

internal object SentryOkHttpUtils {
    internal fun captureClientError(
        scopes: IScopes,
        request: Request,
        response: Response,
    ) {
        // not possible to get a parameterized url, but we remove at least the
        // query string and the fragment.
        // url example: https://api.github.com/users/getsentry/repos/#fragment?query=query
        // url will be: https://api.github.com/users/getsentry/repos/
        // ideally we'd like a parameterized url: https://api.github.com/users/{user}/repos/
        // but that's not possible
        val urlDetails = UrlUtils.parse(request.url.toString())

        val mechanism =
            Mechanism().apply {
                type = "SentryOkHttpInterceptor"
            }
        val exception =
            SentryHttpClientException(
                "HTTP Client Error with status code: ${response.code}",
            )
        val mechanismException = ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
        val event = SentryEvent(mechanismException)

        val hint = Hint()
        hint.set(TypeCheckHint.OKHTTP_REQUEST, request)
        hint.set(TypeCheckHint.OKHTTP_RESPONSE, response)

        val sentryRequest =
            io.sentry.protocol.Request().apply {
                urlDetails.applyToRequest(this)
                // Cookie is only sent if isSendDefaultPii is enabled
                cookies = if (scopes.options.isSendDefaultPii) request.headers["Cookie"] else null
                method = request.method
                headers = getHeaders(scopes, request.headers)

                request.body?.contentLength().ifHasValidLength {
                    bodySize = it
                }
            }

        val sentryResponse =
            io.sentry.protocol.Response().apply {
                // Set-Cookie is only sent if isSendDefaultPii is enabled due to PII
                cookies = if (scopes.options.isSendDefaultPii) response.headers["Set-Cookie"] else null
                headers = getHeaders(scopes, response.headers)
                statusCode = response.code

                response.body?.contentLength().ifHasValidLength {
                    bodySize = it
                }
            }

        event.request = sentryRequest
        event.contexts.setResponse(sentryResponse)

        scopes.captureEvent(event, hint)
    }

    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
        if (this != null && this != -1L) {
            fn.invoke(this)
        }
    }

    private fun getHeaders(
        scopes: IScopes,
        requestHeaders: Headers,
    ): MutableMap<String, String>? {
        // Headers are only sent if isSendDefaultPii is enabled due to PII
        if (!scopes.options.isSendDefaultPii) {
            return null
        }

        val headers = mutableMapOf<String, String>()

        for (i in 0 until requestHeaders.size) {
            val name = requestHeaders.name(i)

            // header is only sent if isn't sensitive
            if (HttpUtils.containsSensitiveHeader(name)) {
                continue
            }

            val value = requestHeaders.value(i)
            headers[name] = value
        }
        return headers
    }
}
