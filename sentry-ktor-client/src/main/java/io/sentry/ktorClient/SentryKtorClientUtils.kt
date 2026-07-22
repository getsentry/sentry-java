package io.sentry.ktorClient

import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Headers
import io.ktor.http.contentLength
import io.ktor.util.toMap
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryDate
import io.sentry.SentryEvent
import io.sentry.SpanDataConvention
import io.sentry.TypeCheckHint
import io.sentry.exception.ExceptionMechanismException
import io.sentry.exception.SentryHttpClientException
import io.sentry.protocol.Mechanism
import io.sentry.util.HttpUtils
import io.sentry.util.UrlUtils

internal object SentryKtorClientUtils {
  internal suspend fun captureClientError(
    scopes: IScopes,
    request: HttpRequest,
    response: HttpResponse,
  ) {
    val urlDetails = UrlUtils.parse(request.url.toString(), scopes.options.dataCollectionResolver)

    val mechanism = Mechanism().apply { type = "SentryKtorClientPlugin" }
    val exception =
      SentryHttpClientException("HTTP Client Error with status code: ${response.status.value}")
    val mechanismException =
      ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
    val event = SentryEvent(mechanismException)

    val sentryRequest =
      io.sentry.protocol.Request().apply {
        urlDetails.applyToRequest(this)
        cookies = getRequestCookies(scopes, request.headers["Cookie"])
        method = request.method.value
        headers = getRequestHeaders(scopes, request.headers)
        bodySize = request.content.contentLength
      }

    val sentryResponse =
      io.sentry.protocol.Response().apply {
        cookies = getResponseCookies(scopes, response.headers["Set-Cookie"])
        headers = getResponseHeaders(scopes, response.headers)
        statusCode = response.status.value
        try {
          bodySize = response.bodyAsBytes().size.toLong()
        } catch (_: Throwable) {}
      }

    event.request = sentryRequest
    event.contexts.setResponse(sentryResponse)

    val hint =
      Hint().also {
        it.set(TypeCheckHint.KTOR_CLIENT_REQUEST, request)
        it.set(TypeCheckHint.KTOR_CLIENT_RESPONSE, response)
      }

    scopes.captureEvent(event, hint)
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

  private fun getRequestHeaders(scopes: IScopes, headers: Headers): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val requestHeaders =
        headers.toMap().mapValues { (_, values) -> values.joinToString(",") }.toMutableMap()
      return HttpUtils.filterHeaders(
          requestHeaders,
          scopes.options.dataCollectionResolver.httpRequestHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, headers)
  }

  private fun getResponseHeaders(scopes: IScopes, headers: Headers): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val responseHeaders =
        headers.toMap().mapValues { (_, values) -> values.joinToString(",") }.toMutableMap()
      return HttpUtils.filterHeaders(
          responseHeaders,
          scopes.options.dataCollectionResolver.httpResponseHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, headers)
  }

  private fun getHeaders(scopes: IScopes, headers: Headers): MutableMap<String, String>? {
    // Headers are only sent if isSendDefaultPii is enabled due to PII
    if (!scopes.options.isSendDefaultPii) {
      return null
    }

    val res = mutableMapOf<String, String>()
    headers.toMap().forEach { (key, values) ->
      if (!HttpUtils.containsSensitiveHeader(key)) {
        res[key] = values.joinToString(",")
      }
    }
    return res
  }

  internal fun addBreadcrumb(
    scopes: IScopes,
    request: HttpRequest,
    response: HttpResponse,
    startTimestamp: SentryDate?,
    endTimestamp: SentryDate?,
  ) {
    val breadcrumb =
      Breadcrumb.http(
        request.url.toString(),
        request.method.value,
        response.status.value,
        scopes.options.dataCollectionResolver,
      )
    breadcrumb.setData(
      SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY,
      response.contentLength(),
    )
    if (startTimestamp != null) {
      breadcrumb.setData(
        SpanDataConvention.HTTP_START_TIMESTAMP,
        startTimestamp.nanoTimestamp() / 1000L,
      )
    }
    if (endTimestamp != null) {
      breadcrumb.setData(
        SpanDataConvention.HTTP_END_TIMESTAMP,
        endTimestamp.nanoTimestamp() / 1000L,
      )
    }

    val hint =
      Hint().also {
        it.set(TypeCheckHint.KTOR_CLIENT_REQUEST, request)
        it.set(TypeCheckHint.KTOR_CLIENT_RESPONSE, response)
      }

    scopes.addBreadcrumb(breadcrumb, hint)
  }
}
