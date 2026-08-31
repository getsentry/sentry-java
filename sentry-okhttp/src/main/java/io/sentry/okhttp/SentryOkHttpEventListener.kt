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
  private val originalEventListenerMap: ConcurrentHashMap<Call, EventListener> = ConcurrentHashMap()

  // Set only by the constructors that wrap a single EventListener instance. Such a listener is
  // shared by every Call anyway, exactly like OkHttp's own EventListener.asFactory(), so it
  // exists independently of the callStart()..callEnd() window and can always be delegated to.
  private var fixedOriginalEventListener: EventListener? = null

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
  ) : this(ScopesAdapter.getInstance(), originalEventListenerCreator = { originalEventListener }) {
    fixedOriginalEventListener = originalEventListener
  }

  public constructor(
    originalEventListenerFactory: Factory
  ) : this(
    ScopesAdapter.getInstance(),
    originalEventListenerCreator = { originalEventListenerFactory.create(it) },
  )

  public constructor(
    scopes: IScopes = ScopesAdapter.getInstance(),
    originalEventListener: EventListener,
  ) : this(scopes, originalEventListenerCreator = { originalEventListener }) {
    fixedOriginalEventListener = originalEventListener
  }

  public constructor(
    scopes: IScopes = ScopesAdapter.getInstance(),
    originalEventListenerFactory: Factory,
  ) : this(scopes, originalEventListenerCreator = { originalEventListenerFactory.create(it) })

  override fun callStart(call: Call) {
    // The EventListener.Factory contract binds a listener to a single call, so the wrapped
    // listener is kept per call instead of in a field shared by all concurrent calls
    val originalEventListener = getOrCreateEventListener(call)
    originalEventListener?.callStart(call)
    // If the wrapped EventListener is ours, we can just delegate the calls,
    // without creating other events that would create duplicates
    if (canCreateEventSpan(originalEventListener)) {
      eventMap[call] = SentryOkHttpEvent(scopes, call.request())
    }
  }

  override fun proxySelectStart(call: Call, url: HttpUrl) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.proxySelectStart(call, url)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(PROXY_SELECT_EVENT)
  }

  override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.proxySelectEnd(call, url, proxies)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.dnsStart(call, domainName)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(DNS_EVENT)
  }

  override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.dnsEnd(call, domainName, inetAddressList)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.connectStart(call, inetSocketAddress, proxy)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(CONNECT_EVENT)
  }

  override fun secureConnectStart(call: Call) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.secureConnectStart(call)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(SECURE_CONNECT_EVENT)
  }

  override fun secureConnectEnd(call: Call, handshake: Handshake?) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.secureConnectEnd(call, handshake)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.connectEnd(call, inetSocketAddress, proxy, protocol)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.connectionAcquired(call, connection)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(CONNECTION_EVENT)
  }

  override fun connectionReleased(call: Call, connection: Connection) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.connectionReleased(call, connection)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(CONNECTION_EVENT)
  }

  override fun requestHeadersStart(call: Call) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.requestHeadersStart(call)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(REQUEST_HEADERS_EVENT)
  }

  override fun requestHeadersEnd(call: Call, request: Request) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.requestHeadersEnd(call, request)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventFinish(REQUEST_HEADERS_EVENT)
  }

  override fun requestBodyStart(call: Call) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.requestBodyStart(call)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(REQUEST_BODY_EVENT)
  }

  override fun requestBodyEnd(call: Call, byteCount: Long) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.requestBodyEnd(call, byteCount)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.requestFailed(call, ioe)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.responseHeadersStart(call)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(RESPONSE_HEADERS_EVENT)
  }

  override fun responseHeadersEnd(call: Call, response: Response) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.responseHeadersEnd(call, response)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.responseBodyStart(call)
    if (!canCreateEventSpan(originalEventListener)) {
      return
    }
    val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
    okHttpEvent.onEventStart(RESPONSE_BODY_EVENT)
  }

  override fun responseBodyEnd(call: Call, byteCount: Long) {
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.responseBodyEnd(call, byteCount)
    if (!canCreateEventSpan(originalEventListener)) {
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
    val originalEventListener = originalEventListenerMap[call]
    originalEventListener?.responseFailed(call, ioe)
    if (!canCreateEventSpan(originalEventListener)) {
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
    // canceled() is not part of the call window: OkHttp may deliver it before callStart() and
    // after callEnd()/callFailed(), because it holds the listener for the whole Call lifetime
    // while we only keep it for the duration of the call.
    val originalEventListener =
      originalEventListenerMap[call]
        ?: fixedOriginalEventListener
        // The call already reached its terminal event, so its listener is gone. Call.cancel() is
        // documented as a no-op for a completed request, thus there is nothing to report, and
        // creating a second listener here would break the Factory contract and leak the entry.
        ?: if (call.isExecuted()) null else getOrCreateEventListener(call)
    originalEventListener?.canceled(call)
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

  // computeIfAbsent, so that a cancel racing callStart() cannot make the Factory produce two
  // listeners for the same Call
  private fun getOrCreateEventListener(call: Call): EventListener? {
    val creator = originalEventListenerCreator ?: return null
    return originalEventListenerMap.computeIfAbsent(call) { creator.invoke(it) }
  }

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
