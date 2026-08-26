package io.sentry.okhttp

import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/**
 * Logs network performance event metrics to Sentry
 *
 * Usage - add instance of [SentryOkHttpEventListener] in
 * [okhttp3.OkHttpClient.Builder.eventListener]
 *
 * ```
 * val client = OkHttpClient.Builder()
 *     .eventListener(SentryOkHttpEventListener())
 *     .addInterceptor(SentryOkHttpInterceptor())
 *     .build()
 * ```
 *
 * If you already use a [okhttp3.EventListener], you can pass it in the constructor.
 *
 * ```
 * val client = OkHttpClient.Builder()
 *     .eventListener(SentryOkHttpEventListener(myEventListener))
 *     .addInterceptor(SentryOkHttpInterceptor())
 *     .build()
 * ```
 */
@Suppress("TooManyFunctions")
public open class SentryOkHttpEventListener(
  private val scopes: IScopes = ScopesAdapter.getInstance(),
  private val originalEventListenerCreator: ((call: Call) -> EventListener)? = null,
) : EventListener() {
  private val originalEventListenerMap: MutableMap<Call, EventListener> = ConcurrentHashMap()

  public companion object {
    internal const val PROXY_SELECT_EVENT = "http.client.proxy_select_ms"
    internal const val DNS_EVENT = "http.client.resolve_dns_ms"
    internal const val CONNECT_EVENT = "http.connect_ms"
    internal const val SECURE_CONNECT_EVENT = "http.connect.secure_connect_ms"
    internal const val CONNECTION_EVENT = "http.connection_ms"
    internal const val REQUEST_HEADERS_EVENT = "http.connection.request_headers_ms"
    internal const val REQUEST_BODY_EVENT = "http.connection.request_body_ms"
    internal const val RESPONSE_HEADERS_EVENT = "http.connection.response_headers_ms"
    internal const val RESPONSE_BODY_EVENT = "http.connection.response_body_ms"

    internal val eventMap: MutableMap<Call, SentryOkHttpEvent> = ConcurrentHashMap()
  }

  public constructor() : this(ScopesAdapter.getInstance(), originalEventListenerCreator = null)

  public constructor(
    originalEventListener: EventListener
  ) : this(ScopesAdapter.getInstance(), originalEventListenerCreator = { originalEventListener })

  public constructor(
    originalEventListenerFactory: Factory
  ) : this(
    ScopesAdapter.getInstance(),
    originalEventListenerCreator = { originalEventListenerFactory.create(it) },
  )

  public constructor(
    scopes: IScopes = ScopesAdapter.getInstance(),
    originalEventListener: EventListener,
  ) : this(scopes, originalEventListenerCreator = { originalEventListener })

  public constructor(
    scopes: IScopes = ScopesAdapter.getInstance(),
    originalEventListenerFactory: Factory,
  ) : this(scopes, originalEventListenerCreator = { originalEventListenerFactory.create(it) })

  override fun callStart(call: Call) {
    // The EventListener.Factory contract binds a listener to a single call, so the wrapped
    // listener is kept per call instead of in a field shared by all concurrent calls
    val originalEventListener = originalEventListenerCreator?.invoke(call)
    if (originalEventListener != null) {
      originalEventListenerMap[call] = originalEventListener
    }
    originalEventListener?.callStart(call)
    // If the wrapped EventListener is ours, we can just delegate the calls,
    // without creating other events that would create duplicates
    if (canCreateEventSpan(originalEventListener)) {
      eventMap[call] = SentryOkHttpEvent(scopes, call.request())
    }
  }

  override fun proxySelectStart(call: Call, url: HttpUrl) {
    originalEventListenerMap[call]?.proxySelectStart(call, url)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(PROXY_SELECT_EVENT)
  }

  override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
    originalEventListenerMap[call]?.proxySelectEnd(call, url, proxies)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(PROXY_SELECT_EVENT) {
      if (proxies.isNotEmpty()) {
        it.setData("proxies", proxies.joinToString { proxy -> proxy.toString() })
      }
    }
  }

  override fun dnsStart(call: Call, domainName: String) {
    originalEventListenerMap[call]?.dnsStart(call, domainName)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(DNS_EVENT)
  }

  override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
    originalEventListenerMap[call]?.dnsEnd(call, domainName, inetAddressList)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(DNS_EVENT) {
      it.setData("domain_name", domainName)
      if (inetAddressList.isNotEmpty()) {
        it.setData("dns_addresses", inetAddressList.joinToString { address -> address.toString() })
      }
    }
  }

  override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
    originalEventListenerMap[call]?.connectStart(call, inetSocketAddress, proxy)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(CONNECT_EVENT)
  }

  override fun secureConnectStart(call: Call) {
    originalEventListenerMap[call]?.secureConnectStart(call)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(SECURE_CONNECT_EVENT)
  }

  override fun secureConnectEnd(call: Call, handshake: Handshake?) {
    originalEventListenerMap[call]?.secureConnectEnd(call, handshake)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(SECURE_CONNECT_EVENT)
  }

  override fun connectEnd(
    call: Call,
    inetSocketAddress: InetSocketAddress,
    proxy: Proxy,
    protocol: Protocol?,
  ) {
    originalEventListenerMap[call]?.connectEnd(call, inetSocketAddress, proxy, protocol)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.setProtocol(protocol?.name)
    okHttpEvent.onEventFinish(CONNECT_EVENT)
  }

  override fun connectFailed(
    call: Call,
    inetSocketAddress: InetSocketAddress,
    proxy: Proxy,
    protocol: Protocol?,
    ioe: IOException,
  ) {
    originalEventListenerMap[call]?.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.setProtocol(protocol?.name)
    okHttpEvent.setError(ioe.message)
    okHttpEvent.onEventFinish(CONNECT_EVENT) {
      it.throwable = ioe
      it.status = SpanStatus.INTERNAL_ERROR
    }
  }

  override fun connectionAcquired(call: Call, connection: Connection) {
    originalEventListenerMap[call]?.connectionAcquired(call, connection)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(CONNECTION_EVENT)
  }

  override fun connectionReleased(call: Call, connection: Connection) {
    originalEventListenerMap[call]?.connectionReleased(call, connection)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(CONNECTION_EVENT)
  }

  override fun requestHeadersStart(call: Call) {
    originalEventListenerMap[call]?.requestHeadersStart(call)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(REQUEST_HEADERS_EVENT)
  }

  override fun requestHeadersEnd(call: Call, request: Request) {
    originalEventListenerMap[call]?.requestHeadersEnd(call, request)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(REQUEST_HEADERS_EVENT)
  }

  override fun requestBodyStart(call: Call) {
    originalEventListenerMap[call]?.requestBodyStart(call)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(REQUEST_BODY_EVENT)
  }

  override fun requestBodyEnd(call: Call, byteCount: Long) {
    originalEventListenerMap[call]?.requestBodyEnd(call, byteCount)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(REQUEST_BODY_EVENT) {
      if (byteCount > 0) {
        it.setData("http.request_content_length", byteCount)
      }
    }
    okHttpEvent.setRequestBodySize(byteCount)
  }

  override fun requestFailed(call: Call, ioe: IOException) {
    originalEventListenerMap[call]?.requestFailed(call, ioe)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.setError(ioe.message)
    // requestFailed can happen after requestHeaders or requestBody.
    // If requestHeaders already finished, we don't change its status.
    okHttpEvent.onEventFinish(REQUEST_HEADERS_EVENT) {
      if (!it.isFinished) {
        it.status = SpanStatus.INTERNAL_ERROR
        it.throwable = ioe
      }
    }
    okHttpEvent.onEventFinish(REQUEST_BODY_EVENT) {
      it.status = SpanStatus.INTERNAL_ERROR
      it.throwable = ioe
    }
  }

  override fun responseHeadersStart(call: Call) {
    originalEventListenerMap[call]?.responseHeadersStart(call)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(RESPONSE_HEADERS_EVENT)
  }

  override fun responseHeadersEnd(call: Call, response: Response) {
    originalEventListenerMap[call]?.responseHeadersEnd(call, response)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.setResponse(response)
    okHttpEvent.onEventFinish(RESPONSE_HEADERS_EVENT) {
      it.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, response.code)
      // Let's not override the status of a span that was set
      if (it.status == null) {
        it.status = SpanStatus.fromHttpStatusCode(response.code)
      }
    }
  }

  override fun responseBodyStart(call: Call) {
    originalEventListenerMap[call]?.responseBodyStart(call)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(RESPONSE_BODY_EVENT)
  }

  override fun responseBodyEnd(call: Call, byteCount: Long) {
    originalEventListenerMap[call]?.responseBodyEnd(call, byteCount)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.setResponseBodySize(byteCount)
    okHttpEvent.onEventFinish(RESPONSE_BODY_EVENT) {
      if (byteCount > 0) {
        it.setData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY, byteCount)
      }
    }
  }

  override fun responseFailed(call: Call, ioe: IOException) {
    originalEventListenerMap[call]?.responseFailed(call, ioe)
    if (!canCreateEventSpan(call)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.setError(ioe.message)
    // responseFailed can happen after responseHeaders or responseBody.
    // If responseHeaders already finished, we don't change its status.
    okHttpEvent.onEventFinish(RESPONSE_HEADERS_EVENT) {
      if (!it.isFinished) {
        it.status = SpanStatus.INTERNAL_ERROR
        it.throwable = ioe
      }
    }
    okHttpEvent.onEventFinish(RESPONSE_BODY_EVENT) {
      it.status = SpanStatus.INTERNAL_ERROR
      it.throwable = ioe
    }
  }

  override fun callEnd(call: Call) {
    originalEventListenerMap.remove(call)?.callEnd(call)
    val okHttpEvent: SentryOkHttpEvent = eventMap.remove(call) ?: return
    okHttpEvent.finish()
  }

  override fun callFailed(call: Call, ioe: IOException) {
    val originalEventListener = originalEventListenerMap.remove(call)
    originalEventListener?.callFailed(call, ioe)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap.remove(call) ?: return
    okHttpEvent.setError(ioe.message)
    okHttpEvent.finish {
      it.status = SpanStatus.INTERNAL_ERROR
      it.throwable = ioe
    }
  }

  override fun canceled(call: Call) {
    originalEventListenerMap[call]?.canceled(call)
  }

  override fun satisfactionFailure(call: Call, response: Response) {
    originalEventListenerMap[call]?.satisfactionFailure(call, response)
  }

  override fun cacheHit(call: Call, response: Response) {
    originalEventListenerMap[call]?.cacheHit(call, response)
  }

  override fun cacheMiss(call: Call) {
    originalEventListenerMap[call]?.cacheMiss(call)
  }

  override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
    originalEventListenerMap[call]?.cacheConditionalHit(call, cachedResponse)
  }

  private fun canCreateEventSpan(call: Call): Boolean =
    canCreateEventSpan(originalEventListenerMap[call])

  private fun canCreateEventSpan(originalEventListener: EventListener?): Boolean {
    // If the wrapped EventListener is ours, we shouldn't create spans, as the originalEventListener
    // already did it
    // In case SentryOkHttpEventListener from sentry-android-okhttp is used, the is check won't work
    // so we check
    // for the class name as well.
    return originalEventListener !is SentryOkHttpEventListener &&
      "io.sentry.android.okhttp.SentryOkHttpEventListener" != originalEventListener?.javaClass?.name
  }
}
