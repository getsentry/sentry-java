package io.sentry.android.okhttp

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint
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
import java.net.URL

/**
 *  Logs network performance event metrics to Sentry
 *
 *  Usage - add instance of [SentryNetworkCallEventListener] in [OkHttpClient.eventListenerFactory]
 */
class SentryNetworkCallEventListener(
    private val hub: IHub = HubAdapter.getInstance()
) : EventListener() {

    companion object {
        private const val CALL_EVENT = "call"
        private const val PROXY_SELECT_EVENT = "proxySelect"
        private const val DNS_EVENT = "dns"
        private const val SECURE_CONNECT_EVENT = "secureConnect"
        private const val CONNECT_EVENT = "connect"
        private const val CONNECTION_EVENT = "connection"
        private const val REQUEST_HEADERS_EVENT = "requestHeaders"
        private const val REQUEST_BODY_EVENT = "requestBody"
        private const val RESPONSE_HEADERS_EVENT = "responseHeaders"
        private const val RESPONSE_BODY_EVENT = "responseBody"

        internal val eventMap: MutableMap<Call, SentryNetworkCallEvent> = HashMap()
    }

    //region Callback functions

    override fun callStart(call: Call) {
        eventMap[call] = SentryNetworkCallEvent(hub, call)
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(PROXY_SELECT_EVENT)
    }

    override fun proxySelectEnd(
        call: Call,
        url: HttpUrl,
        proxies: List<Proxy>
    ) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.finishSpan(PROXY_SELECT_EVENT) {
            if (proxies.isNotEmpty()) {
                it.setData("proxies", proxies.joinToString { proxy -> proxy.toString() })
            }
        }
    }

    override fun dnsStart(call: Call, domainName: String) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(DNS_EVENT)
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<InetAddress>
    ) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.finishSpan(DNS_EVENT) {
            it.setData("domainName", domainName)
            if (inetAddressList.isNotEmpty()) {
                it.setData("dns", inetAddressList.joinToString { address -> address.toString() })
            }
        }
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy
    ) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(CONNECT_EVENT)
    }

    override fun secureConnectStart(call: Call) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(SECURE_CONNECT_EVENT)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.finishSpan(SECURE_CONNECT_EVENT)
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.setProtocol(protocol?.name)
        networkEvent.finishSpan(CONNECT_EVENT)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.setProtocol(protocol?.name)
        networkEvent.setError(ioe.message)
        networkEvent.finishSpan(CONNECT_EVENT) {
            it.throwable = ioe
            it.status = SpanStatus.INTERNAL_ERROR
        }
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(CONNECTION_EVENT)
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.finishSpan(CONNECTION_EVENT)
    }

    override fun requestHeadersStart(call: Call) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(REQUEST_HEADERS_EVENT)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.finishSpan(REQUEST_HEADERS_EVENT)
    }

    override fun requestBodyStart(call: Call) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(REQUEST_BODY_EVENT)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.finishSpan(REQUEST_BODY_EVENT) {
            if (byteCount > 0) {
                it.setData("request_body_size", byteCount)
            }
        }
        networkEvent.setRequestBodyLength(byteCount)
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.setError(ioe.message)
        networkEvent.finishSpan(REQUEST_HEADERS_EVENT) {
            if (!it.isFinished) {
                it.status = SpanStatus.INTERNAL_ERROR
                it.throwable = ioe
            }
        }
        networkEvent.finishSpan(REQUEST_BODY_EVENT) {
            it.status = SpanStatus.INTERNAL_ERROR
            it.throwable = ioe
        }
    }

    override fun responseHeadersStart(call: Call) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(RESPONSE_HEADERS_EVENT)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.setResponse(response)
        networkEvent.finishSpan(RESPONSE_HEADERS_EVENT) {
            it.setData("status_code", response.code)
        }
    }

    override fun responseBodyStart(call: Call) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.startSpan(RESPONSE_BODY_EVENT)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.setResponseBodyLength(byteCount)
        networkEvent.finishSpan(RESPONSE_BODY_EVENT) {
            if (byteCount > 0) {
                it.setData("response_body_size", byteCount)
            }
        }
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.setError(ioe.message)
        networkEvent.finishSpan(RESPONSE_HEADERS_EVENT) {
            if (!it.isFinished) {
                it.status = SpanStatus.INTERNAL_ERROR
                it.throwable = ioe
            }
        }
        networkEvent.finishSpan(RESPONSE_BODY_EVENT) {
            it.status = SpanStatus.INTERNAL_ERROR
            it.throwable = ioe
        }
    }

    override fun callEnd(call: Call) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.finishSpan(CALL_EVENT)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val networkEvent: SentryNetworkCallEvent = eventMap[call] ?: return
        networkEvent.setError(ioe.message)
        networkEvent.finishSpan(CALL_EVENT) {
            it.status = SpanStatus.INTERNAL_ERROR
            it.throwable = ioe
        }
    }

    //endregion

    internal class SentryNetworkCallEvent(private val hub: IHub, private val call: Call) {
        private val eventSpans: MutableMap<String, ISpan> = HashMap()
        private val breadcrumb: Breadcrumb
        internal val rootSpan: ISpan?
        private var response: Response? = null

        init {
            val url: String = call.request().url.toString()
            val trimmedUrl: String = trimUrl(url)
            val host: String = call.request().url.host
            val encodedPath: String = call.request().url.encodedPath
            val method: String = call.request().method
            val requestBodyLength: Long? = call.request().body?.contentLength()

            rootSpan = hub.span?.startChild("http.client", "$method $url")

            breadcrumb = Breadcrumb.http(url, method)
            // todo check PII - remove magic strings - check breadcrumb and span keys
            breadcrumb.setData("URL", url)
            breadcrumb.setData("Filtered URL", trimmedUrl)
            breadcrumb.setData("Host", host)
            breadcrumb.setData("Path", encodedPath)
            breadcrumb.setData("Method", method)
            breadcrumb.setData("Success", true)
            if (requestBodyLength != null && requestBodyLength > -1) {
                breadcrumb.setData("RequestBody Length", requestBodyLength)
            }
        }

        private fun trimUrl(url: String): String {
            val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            val trimmedUrl = url.replace(uuidRegex, "*")
            if (URL(trimmedUrl).query == null) {
                return trimmedUrl
            }
            return trimmedUrl.replace(URL(trimmedUrl).query, "").replace("?", "")
        }

        fun setResponse(response: Response) {
            this.response = response
            breadcrumb.setData("protocol", response.protocol.name)
            breadcrumb.setData("status_code", response.code)
        }

        fun setProtocol(protocolName: String?) {
            if (protocolName != null) {
                breadcrumb.setData("protocol", protocolName)
            }
        }

        fun setRequestBodyLength(byteCount: Long) {
            if (byteCount > -1) {
                breadcrumb.setData("request_body_size", byteCount)
            }
        }

        fun setResponseBodyLength(byteCount: Long) {
            if (byteCount > -1) {
                breadcrumb.setData("response_body_size", byteCount)
            }
        }

        fun setError(errorMessage: String?) {
            breadcrumb.setData("Success", false)
            if (errorMessage != null) {
                breadcrumb.setData("Error Message", errorMessage)
            }
        }

        fun startSpan(event: String) {
            val span = rootSpan?.startChild("http.client", event) ?: return
            Log.e("BBB", "BBB - start $event")
            eventSpans[event] = span
        }

        fun finishSpan(event: String, beforeFinish: ((span: ISpan) -> Unit)? = null) {
            if (event == CALL_EVENT) {
                if (rootSpan == null) {
                    eventMap.remove(call)
                    return
                }
                eventSpans.values.filter { !it.isFinished }.forEach { it.finish(SpanStatus.DEADLINE_EXCEEDED) }
                beforeFinish?.invoke(rootSpan)
                rootSpan.finish()

                val hint = Hint()
                hint.set(TypeCheckHint.OKHTTP_REQUEST, call.request())
                response?.let { hint.set(TypeCheckHint.OKHTTP_RESPONSE, it) }

                hub.addBreadcrumb(breadcrumb, hint)
                return
            }
            val span = eventSpans[event] ?: return
            Log.e("BBB", "BBB - finish $event")
            beforeFinish?.invoke(span)
            span.finish()
        }
    }
}
