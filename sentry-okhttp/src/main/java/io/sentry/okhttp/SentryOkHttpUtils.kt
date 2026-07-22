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
  internal fun captureClientError(scopes: IScopes, request: Request, response: Response) {
    // not possible to get a parameterized url, but we remove at least the
    // query string and the fragment.
    // url example: https://api.github.com/users/getsentry/repos/#fragment?query=query
    // url will be: https://api.github.com/users/getsentry/repos/
    // ideally we'd like a parameterized url: https://api.github.com/users/{user}/repos/
    // but that's not possible
    val urlDetails = UrlUtils.parse(request.url.toString(), scopes.options.dataCollectionResolver)

    val mechanism = Mechanism().apply { type = "SentryOkHttpInterceptor" }
    val exception =
      SentryHttpClientException("HTTP Client Error with status code: ${response.code}")
    val mechanismException =
      ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
    val event = SentryEvent(mechanismException)

    val hint = Hint()
    hint.set(TypeCheckHint.OKHTTP_REQUEST, request)
    hint.set(TypeCheckHint.OKHTTP_RESPONSE, response)

    val sentryRequest =
      io.sentry.protocol.Request().apply {
        urlDetails.applyToRequest(this)
        cookies = getRequestCookies(scopes, request.headers["Cookie"])
        method = request.method
        headers = getRequestHeaders(scopes, request.headers)

        request.body?.contentLength().ifHasValidLength { bodySize = it }
      }

    val sentryResponse =
      io.sentry.protocol.Response().apply {
        cookies = getResponseCookies(scopes, response.headers["Set-Cookie"])
        headers = getResponseHeaders(scopes, response.headers)
        statusCode = response.code

        response.body?.contentLength().ifHasValidLength { bodySize = it }
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

  private fun getRequestCookies(scopes: IScopes, cookies: String?): String? =
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      HttpUtils.filterCookies(
        cookies,
        scopes.options.dataCollectionResolver.cookies,
        null,
      )
    } else if (scopes.options.isSendDefaultPii) {
      cookies
    } else {
      null
    }

  private fun getResponseCookies(scopes: IScopes, cookies: String?): String? =
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      HttpUtils.filterSetCookie(cookies, scopes.options.dataCollectionResolver.cookies)
    } else if (scopes.options.isSendDefaultPii) {
      cookies
    } else {
      null
    }

  private fun getRequestHeaders(
    scopes: IScopes,
    requestHeaders: Headers,
  ): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val headers = mutableMapOf<String, String>()
      for (i in 0 until requestHeaders.size) {
        headers[requestHeaders.name(i)] = requestHeaders.value(i)
      }
      return HttpUtils.filterHeaders(
          headers,
          scopes.options.dataCollectionResolver.httpRequestHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, requestHeaders)
  }

  private fun getResponseHeaders(
    scopes: IScopes,
    responseHeaders: Headers,
  ): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val headers = mutableMapOf<String, String>()
      for (i in 0 until responseHeaders.size) {
        headers[responseHeaders.name(i)] = responseHeaders.value(i)
      }
      return HttpUtils.filterHeaders(
          headers,
          scopes.options.dataCollectionResolver.httpResponseHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, responseHeaders)
  }

  private fun getHeaders(scopes: IScopes, requestHeaders: Headers): MutableMap<String, String>? {
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
