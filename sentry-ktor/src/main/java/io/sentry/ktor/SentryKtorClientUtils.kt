package io.sentry.ktor

import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Headers
import io.ktor.http.contentLength
import io.ktor.util.toMap
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
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
    val urlDetails = UrlUtils.parse(request.url.toString())

    val mechanism = Mechanism().apply { type = "SentryKtorClientPlugin" }
    val exception =
      SentryHttpClientException("HTTP Client Error with status code: ${response.status.value}")
    val mechanismException =
      ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
    val event = SentryEvent(mechanismException)

    val hint = Hint()
    hint.set(TypeCheckHint.KTOR_REQUEST, request)
    hint.set(TypeCheckHint.KTOR_RESPONSE, response)

    val sentryRequest =
      io.sentry.protocol.Request().apply {
        // Cookie is only sent if isSendDefaultPii is enabled
        urlDetails.applyToRequest(this)
        cookies = if (scopes.options.isSendDefaultPii) request.headers["Cookie"] else null
        method = request.method.value
        headers = getHeaders(scopes, request.headers)
        bodySize = request.content.contentLength
      }

    val sentryResponse =
      io.sentry.protocol.Response().apply {
        // Set-Cookie is only sent if isSendDefaultPii is enabled due to PII
        cookies = if (scopes.options.isSendDefaultPii) response.headers["Set-Cookie"] else null
        headers = getHeaders(scopes, response.headers)
        statusCode = response.status.value
        bodySize = response.bodyAsBytes().size.toLong()
      }

    event.request = sentryRequest
    event.contexts.setResponse(sentryResponse)

    scopes.captureEvent(event, hint)
  }

  private fun getHeaders(scopes: IScopes, headers: Headers): MutableMap<String, String>? {
    // Headers are only sent if isSendDefaultPii is enabled due to PII
    if (!scopes.options.isSendDefaultPii) {
      return null
    }

    val res = mutableMapOf<String, String>()
    headers.toMap().forEach { (key, values) ->
      if (!HttpUtils.containsSensitiveHeader(key)) {
        if (values.size == 1) {
          res[key] = values[0]
        } else {
          for ((i, value) in values.withIndex()) {
            res["$key[$i]"] = value
          }
        }
      }
    }
    return res
  }

  internal fun addBreadcrumb(
    scopes: IScopes,
    request: HttpRequest,
    response: HttpResponse,
    startTimestamp: Long?,
    endTimestamp: Long?,
  ) {
    val breadcrumb =
      Breadcrumb.http(request.url.toString(), request.method.value, response.status.value)
    breadcrumb.setData(
      SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY,
      response.contentLength(),
    )
    if (startTimestamp != null) {
      breadcrumb.setData(SpanDataConvention.HTTP_START_TIMESTAMP, startTimestamp)
    }
    if (endTimestamp != null) {
      breadcrumb.setData(SpanDataConvention.HTTP_END_TIMESTAMP, endTimestamp)
    }

    val hint = Hint().also { it.set(TypeCheckHint.KTOR_REQUEST, request) }
    hint[TypeCheckHint.KTOR_REQUEST] = request
    hint[TypeCheckHint.KTOR_RESPONSE] = response

    scopes.addBreadcrumb(breadcrumb, hint)
  }
}
