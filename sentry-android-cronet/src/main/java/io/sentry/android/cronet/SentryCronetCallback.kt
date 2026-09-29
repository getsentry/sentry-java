package io.sentry.android.cronet

import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.transport.CurrentDateProvider
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.SpanUtils
import io.sentry.util.UrlUtils
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.jetbrains.annotations.ApiStatus

/**
 * Opt-in tracing and breadcrumbs for one Cronet request. Pass this callback to Cronet's request
 * builder, then call [start] instead of [UrlRequest.start]. Do not reuse it for another request.
 *
 * The supplied method must match the request builder (including POST for uploads). Redirect and
 * response-body flow control remain the delegate's responsibility. No request headers are added.
 */
@ApiStatus.Experimental
public class SentryCronetCallback
@JvmOverloads
public constructor(
  private val url: String,
  private val method: String,
  private val delegate: UrlRequest.Callback,
  private val scopes: IScopes = Sentry.getCurrentScopes(),
) : UrlRequest.Callback() {
  private companion object {
    private const val TRACE_ORIGIN = "auto.http.cronet"

    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-cronet", BuildConfig.VERSION_NAME)
    }
  }

  private val started = AtomicBoolean(false)
  private val finished = AtomicBoolean(false)
  private var span: ISpan? = null
  private var startTimestamp: Long = 0

  init {
    addIntegrationToSdkVersion("Cronet")
  }

  /** Starts the request and its span together, excluding time spent configuring the builder. */
  public fun start(request: UrlRequest) {
    check(started.compareAndSet(false, true)) { "Use a new SentryCronetCallback for each request." }
    startTimestamp = CurrentDateProvider.getInstance().currentTimeMillis
    val details = UrlUtils.parse(url, scopes.options.dataCollectionResolver)
    if (!SpanUtils.isIgnored(scopes.options.ignoredSpanOrigins, TRACE_ORIGIN)) {
      span = scopes.transaction?.startChild("http.client", "$method ${details.urlOrFallback}")
      span?.spanContext?.origin = TRACE_ORIGIN
      span?.setData(SpanDataConvention.HTTP_METHOD_KEY, method)
      details.applyToSpan(span)
    }
    try {
      request.start()
    } catch (e: RuntimeException) {
      finish(null, SpanStatus.INTERNAL_ERROR, e)
      throw e
    }
  }

  @Throws(Exception::class)
  override fun onRedirectReceived(
    request: UrlRequest,
    info: UrlResponseInfo,
    newLocationUrl: String,
  ) {
    delegate.onRedirectReceived(request, info, newLocationUrl)
  }

  @Throws(Exception::class)
  override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
    delegate.onResponseStarted(request, info)
  }

  @Throws(Exception::class)
  override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
    delegate.onReadCompleted(request, info, byteBuffer)
  }

  override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
    finish(info, SpanStatus.fromHttpStatusCode(info.httpStatusCode), null)
    delegate.onSucceeded(request, info)
  }

  override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
    finish(info, SpanStatus.INTERNAL_ERROR, error)
    delegate.onFailed(request, info, error)
  }

  override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
    finish(info, SpanStatus.CANCELLED, null)
    delegate.onCanceled(request, info)
  }

  private fun finish(info: UrlResponseInfo?, status: SpanStatus?, error: Throwable?) {
    if (!started.get() || !finished.compareAndSet(false, true)) {
      return
    }
    val breadcrumb =
      Breadcrumb.http(url, method, info?.httpStatusCode, scopes.options.dataCollectionResolver)
    if (info != null) {
      span?.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, info.httpStatusCode)
      if (info.negotiatedProtocol.isNotEmpty()) {
        span?.setData("protocol", info.negotiatedProtocol)
        breadcrumb.setData("protocol", info.negotiatedProtocol)
      }
    }
    span?.throwable = error
    span?.status = status
    span?.finish()
    breadcrumb.setData(SpanDataConvention.HTTP_START_TIMESTAMP, startTimestamp)
    breadcrumb.setData(
      SpanDataConvention.HTTP_END_TIMESTAMP,
      CurrentDateProvider.getInstance().currentTimeMillis,
    )
    scopes.addBreadcrumb(breadcrumb)
  }
}
