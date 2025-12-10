package io.sentry.okhttp

import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HttpStatusCodeRange
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.SentryReplayOptions
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint.OKHTTP_REQUEST
import io.sentry.TypeCheckHint.OKHTTP_RESPONSE
import io.sentry.TypeCheckHint.SENTRY_REPLAY_NETWORK_DETAILS
import io.sentry.okhttp.SentryOkHttpInterceptor.BeforeSpanCallback
import io.sentry.transport.CurrentDateProvider
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.Platform
import io.sentry.util.PropagationTargetsUtils
import io.sentry.util.SpanUtils
import io.sentry.util.TracingUtils
import io.sentry.util.UrlUtils
import io.sentry.util.network.NetworkBody
import io.sentry.util.network.NetworkBodyParser
import io.sentry.util.network.NetworkDetailCaptureUtils
import io.sentry.util.network.NetworkRequestData
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jetbrains.annotations.VisibleForTesting

/**
 * The Sentry's [SentryOkHttpInterceptor], it will automatically add a breadcrumb and start a span
 * out of the active span bound to the scope for each HTTP Request. If [captureFailedRequests] is
 * enabled, the SDK will capture HTTP Client errors as well.
 *
 * @param scopes The [IScopes], internal and only used for testing.
 * @param beforeSpan The [ISpan] can be customized or dropped with the [BeforeSpanCallback].
 * @param captureFailedRequests The SDK will only capture HTTP Client errors if it is enabled,
 *   Defaults to true.
 * @param failedRequestStatusCodes The SDK will only capture HTTP Client errors if the HTTP Response
 *   status code is within the defined ranges.
 * @param failedRequestTargets The SDK will only capture HTTP Client errors if the HTTP Request URL
 *   is a match for any of the defined targets.
 */
public open class SentryOkHttpInterceptor(
  private val scopes: IScopes = ScopesAdapter.getInstance(),
  private val beforeSpan: BeforeSpanCallback? = null,
  private val captureFailedRequests: Boolean = true,
  private val failedRequestStatusCodes: List<HttpStatusCodeRange> =
    listOf(HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX)),
  private val failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
) : Interceptor {
  private companion object {
    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-okhttp", BuildConfig.VERSION_NAME)
    }
  }

  public constructor() : this(ScopesAdapter.getInstance())

  public constructor(scopes: IScopes) : this(scopes, null)

  public constructor(beforeSpan: BeforeSpanCallback) : this(ScopesAdapter.getInstance(), beforeSpan)

  init {
    addIntegrationToSdkVersion("OkHttp")
  }

  @Suppress("LongMethod")
  override fun intercept(chain: Interceptor.Chain): Response {
    var request = chain.request()

    val urlDetails = UrlUtils.parse(request.url.toString())
    val url = urlDetails.urlOrFallback
    val method = request.method

    val span: ISpan?
    val okHttpEvent: SentryOkHttpEvent?

    if (SentryOkHttpEventListener.eventMap.containsKey(chain.call())) {
      // read the span from the event listener
      okHttpEvent = SentryOkHttpEventListener.eventMap[chain.call()]
      span = okHttpEvent?.callSpan
    } else {
      // read the span from the bound scope
      okHttpEvent = null
      val parentSpan = if (Platform.isAndroid()) scopes.transaction else scopes.span
      span = parentSpan?.startChild("http.client", "$method $url")
    }

    val startTimestamp = CurrentDateProvider.getInstance().currentTimeMillis

    span?.spanContext?.origin = TRACE_ORIGIN

    urlDetails.applyToSpan(span)

    val isFromEventListener = okHttpEvent != null
    var response: Response? = null
    var code: Int? = null

    val networkDetailData =
      NetworkDetailCaptureUtils.initializeForUrl(
        request.url.toString(),
        request.method,
        scopes.options.sessionReplay.networkDetailAllowUrls,
        scopes.options.sessionReplay.networkDetailDenyUrls,
      )

    try {
      val requestBuilder = request.newBuilder()

      if (!isIgnored()) {
        TracingUtils.traceIfAllowed(
            scopes,
            request.url.toString(),
            request.headers(BaggageHeader.BAGGAGE_HEADER),
            span,
          )
          ?.let { tracingHeaders ->
            requestBuilder.addHeader(
              tracingHeaders.sentryTraceHeader.name,
              tracingHeaders.sentryTraceHeader.value,
            )
            tracingHeaders.baggageHeader?.let {
              requestBuilder.removeHeader(BaggageHeader.BAGGAGE_HEADER)
              requestBuilder.addHeader(it.name, it.value)
            }
            tracingHeaders.w3cTraceparentHeader?.let { requestBuilder.addHeader(it.name, it.value) }
          }
      }

      val requestContentLength = request.body?.contentLength()

      networkDetailData?.setRequestDetails(
        NetworkDetailCaptureUtils.createRequest(
          request,
          requestContentLength,
          scopes.options.sessionReplay.isNetworkCaptureBodies,
          { req ->
            req.body?.let { originalBody ->
              val buffer = okio.Buffer()
              originalBody.writeTo(buffer)
              val bodyBytes = buffer.readByteArray()

              // Create fresh RequestBody and update the request being built
              val newRequestBody = bodyBytes.toRequestBody(originalBody.contentType())
              requestBuilder.method(request.method, newRequestBody)

              // Parse the buffered bytes into NetworkBody for capture
              safeExtractRequestBody(bodyBytes, originalBody.contentType(), scopes.options.logger)
            }
          },
          scopes.options.sessionReplay.networkRequestHeaders,
          { req: Request -> req.headers.toMap() },
        )
      )

      request = requestBuilder.build()
      response = chain.proceed(request)
      code = response.code
      span?.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, code)
      span?.status = SpanStatus.fromHttpStatusCode(code)

      // OkHttp errors (4xx, 5xx) don't throw, so it's safe to call within this block.
      // breadcrumbs are added on the finally block because we'd like to know if the device
      // had an unstable connection or something similar
      if (shouldCaptureClientError(request, response)) {
        // If we capture the client error directly, it could be associated with the
        // currently running span by the backend. In case the listener is in use, that is
        // an inner span. So, if the listener is in use, we let it capture the client
        // error, to shown it in the http root call span in the dashboard.
        if (isFromEventListener && okHttpEvent != null) {
          okHttpEvent.setClientErrorResponse(response)
        } else {
          SentryOkHttpUtils.captureClientError(scopes, request, response)
        }
      }

      return response
    } catch (e: IOException) {
      span?.apply {
        this.throwable = e
        this.status = SpanStatus.INTERNAL_ERROR
      }
      throw e
    } finally {
      // interceptors may change the request details, so let's update it here
      // this only works correctly if SentryOkHttpInterceptor is the last one in the chain
      okHttpEvent?.setRequest(request)

      response?.let {
        networkDetailData?.setResponseDetails(
          it.code,
          NetworkDetailCaptureUtils.createResponse(
            it,
            it.body?.contentLength(),
            scopes.options.sessionReplay.isNetworkCaptureBodies,
            { resp: Response -> resp.extractResponseBody(scopes.options.logger) },
            scopes.options.sessionReplay.networkResponseHeaders,
            { resp: Response -> resp.headers.toMap() },
          ),
        )
      }

      // Set network details on the OkHttpEvent so it can include them in the breadcrumb hint
      okHttpEvent?.setNetworkDetails(networkDetailData)

      finishSpan(span, request, response, isFromEventListener, okHttpEvent)

      // The SentryOkHttpEventListener will send the breadcrumb itself if used for this call
      if (!isFromEventListener) {
        sendBreadcrumb(request, code, response, startTimestamp, networkDetailData)
      }
    }
  }

  private fun isIgnored(): Boolean =
    SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN)

  private fun sendBreadcrumb(
    request: Request,
    code: Int?,
    response: Response?,
    startTimestamp: Long,
    networkDetailData: NetworkRequestData?,
  ) {
    val breadcrumb = Breadcrumb.http(request.url.toString(), request.method, code)

    // Track request and response body sizes for the breadcrumb
    request.body?.contentLength().ifHasValidLength {
      breadcrumb.setData("http.request_content_length", it)
    }

    response?.body?.contentLength().ifHasValidLength {
      breadcrumb.setData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY, it)
    }

    val hint =
      Hint().also {
        it.set(OKHTTP_REQUEST, request)
        response?.let { resp -> it[OKHTTP_RESPONSE] = resp }

        if (networkDetailData != null) {
          it.set(SENTRY_REPLAY_NETWORK_DETAILS, networkDetailData)
        }
      }

    // needs this as unix timestamp for rrweb
    breadcrumb.setData(SpanDataConvention.HTTP_START_TIMESTAMP, startTimestamp)
    breadcrumb.setData(
      SpanDataConvention.HTTP_END_TIMESTAMP,
      CurrentDateProvider.getInstance().currentTimeMillis,
    )

    scopes.addBreadcrumb(breadcrumb, hint)
  }

  /** Extracts headers from OkHttp Headers object into a map */
  @VisibleForTesting
  internal fun okhttp3.Headers.toMap(): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    for (i in 0 until size) {
      val name = name(i)
      val value = value(i)
      val existingValue = headers[name]
      if (existingValue != null) {
        // Concatenate duplicate headers with semicolon separator
        headers[name] = "$existingValue; $value"
      } else {
        headers[name] = value
      }
    }
    return headers
  }

  /** Extracts NetworkBody from already buffered request body data. */
  private fun safeExtractRequestBody(
    bufferedBody: ByteArray?,
    contentType: okhttp3.MediaType?,
    logger: ILogger,
  ): NetworkBody? {
    if (bufferedBody == null) {
      return null
    }

    try {
      val contentTypeString = contentType?.toString()
      val maxBodySize = SentryReplayOptions.MAX_NETWORK_BODY_SIZE
      val charset = contentType?.charset(Charsets.UTF_8)?.name() ?: "UTF-8"

      return NetworkBodyParser.fromBytes(
        bufferedBody,
        contentTypeString,
        charset,
        maxBodySize,
        logger,
      )
    } catch (e: Exception) {
      logger.log(io.sentry.SentryLevel.ERROR, "Failed to parse buffered request body: ${e.message}")
      return null
    }
  }

  /** Extracts the body content from an OkHttp Response safely */
  private fun Response.extractResponseBody(logger: ILogger): NetworkBody? {
    return body?.let { responseBody ->
      try {
        val contentType = responseBody.contentType()
        val contentTypeString = contentType?.toString()
        val maxBodySize = SentryReplayOptions.MAX_NETWORK_BODY_SIZE

        // Peek at the body (doesn't consume it)
        val peekBody = peekBody(maxBodySize.toLong())
        val bodyBytes = peekBody.bytes()

        val charset = contentType?.charset(Charsets.UTF_8)?.name() ?: "UTF-8"
        return NetworkBodyParser.fromBytes(
          bodyBytes,
          contentTypeString,
          charset,
          maxBodySize,
          logger,
        )
      } catch (e: Exception) {
        logger.log(
          io.sentry.SentryLevel.ERROR,
          "Failed to read http response body for Network Details: ${e.message}",
        )
        null
      }
    }
  }

  private fun finishSpan(
    span: ISpan?,
    request: Request,
    response: Response?,
    isFromEventListener: Boolean,
    okHttpEvent: SentryOkHttpEvent?,
  ) {
    if (span == null) {
      // tracing can be disabled, or there can be no active span, but we still want to finalize the
      // OkHttpEvent when both SentryOkHttpInterceptor and SentryOkHttpEventListener are used
      okHttpEvent?.finish()
      return
    }
    if (beforeSpan != null) {
      val result = beforeSpan.execute(span, request, response)
      if (result == null) {
        // span is dropped
        span.spanContext.sampled = false
      }
    }
    if (!isFromEventListener) {
      span.finish()
    }
    // The SentryOkHttpEventListener waits until the response is closed (which may never happen), so
    // we close it here
    okHttpEvent?.finish()
  }

  private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
    if (this != null && this != -1L) {
      fn.invoke(this)
    }
  }

  private fun shouldCaptureClientError(request: Request, response: Response): Boolean {
    // return if the feature is disabled or its not within the range
    if (!captureFailedRequests || !containsStatusCode(response.code)) {
      return false
    }

    // return if its not a target match
    if (!PropagationTargetsUtils.contain(failedRequestTargets, request.url.toString())) {
      return false
    }

    return true
  }

  private fun containsStatusCode(statusCode: Int): Boolean {
    for (item in failedRequestStatusCodes) {
      if (item.isInRange(statusCode)) {
        return true
      }
    }
    return false
  }

  /** The BeforeSpan callback */
  public fun interface BeforeSpanCallback {
    /**
     * Mutates or drops span before being added
     *
     * @param span the span to mutate or drop
     * @param request the HTTP request executed by okHttp
     * @param response the HTTP response received by okHttp
     */
    public fun execute(span: ISpan, request: Request, response: Response?): ISpan?
  }
}
