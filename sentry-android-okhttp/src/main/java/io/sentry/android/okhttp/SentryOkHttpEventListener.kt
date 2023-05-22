package io.sentry.android.okhttp

import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.SpanStatus
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 *  Logs network performance event metrics to Sentry
 *
 *  Usage - add instance of [SentryOkHttpEventListener] in [OkHttpClient.eventListener]
 *
 * ```
 * val client = OkHttpClient.Builder()
 *     .eventListener(SentryOkHttpEventListener())
 *     .addInterceptor(SentryOkHttpInterceptor())
 *     .build()
 * ```
 *
 * If you already use a [OkHttpClient.eventListener], you can pass it in the constructor.
 *
 * ```
 * val client = OkHttpClient.Builder()
 *     .eventListener(SentryOkHttpEventListener(myEventListener))
 *     .addInterceptor(SentryOkHttpInterceptor())
 *     .build()
 * ```
 */
@Suppress("TooManyFunctions")
class SentryOkHttpEventListener(
    private val hub: IHub = HubAdapter.getInstance(),
    private val originalEventListenerCreator: ((call: Call) -> EventListener)? = null
) : EventListener() {

    private var originalEventListener: EventListener? = null

    companion object {
        internal const val PROXY_SELECT_EVENT = "proxySelect"
        internal const val DNS_EVENT = "dns"
        internal const val SECURE_CONNECT_EVENT = "secureConnect"
        internal const val CONNECT_EVENT = "connect"
        internal const val CONNECTION_EVENT = "connection"
        internal const val REQUEST_HEADERS_EVENT = "requestHeaders"
        internal const val REQUEST_BODY_EVENT = "requestBody"
        internal const val RESPONSE_HEADERS_EVENT = "responseHeaders"
        internal const val RESPONSE_BODY_EVENT = "responseBody"

        internal val eventMap: MutableMap<Call, SentryOkHttpEvent> = HashMap()
    }

    constructor(hub: IHub = HubAdapter.getInstance(), originalEventListener: EventListener) : this(
        hub,
        originalEventListenerCreator = { originalEventListener }
    )

    constructor(hub: IHub = HubAdapter.getInstance(), originalEventListenerFactory: Factory) : this(
        hub,
        originalEventListenerCreator = { originalEventListenerFactory.create(it) }
    )

    override fun callStart(call: Call) {
        originalEventListener = originalEventListenerCreator?.invoke(call)
        originalEventListener?.callStart(call)
        // If the wrapped EventListener is ours, we can just delegate the calls,
        // without creating other events that would create duplicates
        if (canCreateEventSpan()) {
            eventMap[call] = SentryOkHttpEvent(hub, call.request())
        }
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        originalEventListener?.proxySelectStart(call, url)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(PROXY_SELECT_EVENT)
    }

    override fun proxySelectEnd(
        call: Call,
        url: HttpUrl,
        proxies: List<Proxy>
    ) {
        originalEventListener?.proxySelectEnd(call, url, proxies)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.finishSpan(PROXY_SELECT_EVENT) {
            if (proxies.isNotEmpty()) {
                it.setData("proxies", proxies.joinToString { proxy -> proxy.toString() })
            }
        }
    }

    override fun dnsStart(call: Call, domainName: String) {
        originalEventListener?.dnsStart(call, domainName)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(DNS_EVENT)
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<InetAddress>
    ) {
        originalEventListener?.dnsEnd(call, domainName, inetAddressList)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.finishSpan(DNS_EVENT) {
            it.setData("domain_name", domainName)
            if (inetAddressList.isNotEmpty()) {
                it.setData("dns_addresses", inetAddressList.joinToString { address -> address.toString() })
            }
        }
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy
    ) {
        originalEventListener?.connectStart(call, inetSocketAddress, proxy)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(CONNECT_EVENT)
    }

    override fun secureConnectStart(call: Call) {
        originalEventListener?.secureConnectStart(call)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(SECURE_CONNECT_EVENT)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        originalEventListener?.secureConnectEnd(call, handshake)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.finishSpan(SECURE_CONNECT_EVENT)
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        originalEventListener?.connectEnd(call, inetSocketAddress, proxy, protocol)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.setProtocol(protocol?.name)
        okHttpEvent.finishSpan(CONNECT_EVENT)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        originalEventListener?.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.setProtocol(protocol?.name)
        okHttpEvent.setError(ioe.message)
        okHttpEvent.finishSpan(CONNECT_EVENT) {
            it.throwable = ioe
            it.status = SpanStatus.INTERNAL_ERROR
        }
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        originalEventListener?.connectionAcquired(call, connection)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(CONNECTION_EVENT)
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        originalEventListener?.connectionReleased(call, connection)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.finishSpan(CONNECTION_EVENT)
    }

    override fun requestHeadersStart(call: Call) {
        originalEventListener?.requestHeadersStart(call)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(REQUEST_HEADERS_EVENT)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        originalEventListener?.requestHeadersEnd(call, request)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.finishSpan(REQUEST_HEADERS_EVENT)
    }

    override fun requestBodyStart(call: Call) {
        originalEventListener?.requestBodyStart(call)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(REQUEST_BODY_EVENT)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        originalEventListener?.requestBodyEnd(call, byteCount)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.finishSpan(REQUEST_BODY_EVENT) {
            if (byteCount > 0) {
                it.setData("http.request_content_length", byteCount)
            }
        }
        okHttpEvent.setRequestBodySize(byteCount)
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        originalEventListener?.requestFailed(call, ioe)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.setError(ioe.message)
        // requestFailed can happen after requestHeaders or requestBody.
        // If requestHeaders already finished, we don't change its status.
        okHttpEvent.finishSpan(REQUEST_HEADERS_EVENT) {
            if (!it.isFinished) {
                it.status = SpanStatus.INTERNAL_ERROR
                it.throwable = ioe
            }
        }
        okHttpEvent.finishSpan(REQUEST_BODY_EVENT) {
            it.status = SpanStatus.INTERNAL_ERROR
            it.throwable = ioe
        }
    }

    override fun responseHeadersStart(call: Call) {
        originalEventListener?.responseHeadersStart(call)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(RESPONSE_HEADERS_EVENT)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        originalEventListener?.responseHeadersEnd(call, response)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.setResponse(response)
        okHttpEvent.finishSpan(RESPONSE_HEADERS_EVENT) {
            it.setData("status_code", response.code)
            it.status = SpanStatus.fromHttpStatusCode(response.code)
        }
    }

    override fun responseBodyStart(call: Call) {
        originalEventListener?.responseBodyStart(call)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.startSpan(RESPONSE_BODY_EVENT)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        originalEventListener?.responseBodyEnd(call, byteCount)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.setResponseBodySize(byteCount)
        okHttpEvent.finishSpan(RESPONSE_BODY_EVENT) {
            if (byteCount > 0) {
                it.setData("http.response_content_length", byteCount)
            }
        }
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        originalEventListener?.responseFailed(call, ioe)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap[call] ?: return
        okHttpEvent.setError(ioe.message)
        // responseFailed can happen after responseHeaders or responseBody.
        // If responseHeaders already finished, we don't change its status.
        okHttpEvent.finishSpan(RESPONSE_HEADERS_EVENT) {
            if (!it.isFinished) {
                it.status = SpanStatus.INTERNAL_ERROR
                it.throwable = ioe
            }
        }
        okHttpEvent.finishSpan(RESPONSE_BODY_EVENT) {
            it.status = SpanStatus.INTERNAL_ERROR
            it.throwable = ioe
        }
    }

    override fun callEnd(call: Call) {
        originalEventListener?.callEnd(call)
        val okHttpEvent: SentryOkHttpEvent = eventMap.remove(call) ?: return
        okHttpEvent.finishEvent()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        originalEventListener?.callFailed(call, ioe)
        if (!canCreateEventSpan()) {
            return
        }
        val okHttpEvent: SentryOkHttpEvent = eventMap.remove(call) ?: return
        okHttpEvent.setError(ioe.message)
        okHttpEvent.finishEvent {
            it.status = SpanStatus.INTERNAL_ERROR
            it.throwable = ioe
        }
    }

    private fun canCreateEventSpan(): Boolean {
        // If the wrapped EventListener is ours, we shouldn't create spans, as the originalEventListener already did it
        return originalEventListener !is SentryOkHttpEventListener
    }
}
