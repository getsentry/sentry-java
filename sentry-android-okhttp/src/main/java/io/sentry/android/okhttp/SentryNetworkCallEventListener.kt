package io.sentry.android.okhttp

import io.sentry.*
import okhttp3.EventListener
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Connection
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
    private val hub: IHub = HubAdapter.getInstance(),
) : EventListener() {

    companion object {
        private const val CALL_START = "callStart"

        private const val PROXY_SELECT_START = "proxySelectStart"
        private const val PROXY_SELECT_END = "proxySelectEnd"

        private const val DNS_START = "dnsStart"
        private const val DNS_END = "dnsEnd"

        private const val CONNECT_START = "connectStart"
        private const val SECURE_CONNECT_START = "secureConnectStart"
        private const val SECURE_CONNECT_END = "secureConnectEnd"

        private const val CONNECT_END = "connectEnd"
        private const val CONNECT_FAILED = "connectFailed"
        private const val CONNECTION_ACQUIRED = "connectionAcquired"
        private const val CONNECTION_RELEASED = "connectionReleased"

        private const val REQUEST_HEADERS_START = "requestHeadersStart"
        private const val REQUEST_HEADERS_END = "requestHeadersEnd"
        private const val REQUEST_BODY_START = "requestBodyStart"
        private const val REQUEST_BODY_END = "requestBodyEnd"
        private const val REQUEST_FAILED = "requestFailed"

        private const val RESPONSE_HEADERS_START = "responseHeadersStart"
        private const val RESPONSE_HEADERS_END = "responseHeadersEnd"
        private const val RESPONSE_BODY_START = "responseBodyStart"
        private const val RESPONSE_BODY_END = "responseBodyEnd"
        private const val RESPONSE_FAILED = "responseFailed"

        private const val CALL_END = "callEnd"
        private const val CALL_FAILED = "callFailed"

        private const val NETWORK_PERF = "Network Performance"
    }

    val eventMetrics = mutableListOf<EventMetric>()
    private var startTime: Long = System.currentTimeMillis()
    private var host: String? = null
    private var encodedPath: String? = null
    private var method: String? = null
    private var requestBodyLength: Long? = null
    private var responseBodyLength: Long? = null
    private var wasCallSuccessful: Boolean? = null
    private var statusCode: Int? = null
    private var errorMessage: String? = null
    private var failureReason: String? = null
    private var protocol: String? = null

    /**
     * Adds [event] to [eventMetrics] list. If a terminal event [CALL_END] or [CALL_FAILED]
     * is received, metrics are calculated and logged on Sentry.
     */
    fun consumeEvent(event: String, url: String) {
        val trimmedUrl = removeParams(removeIdsFromUrl(url))
        eventMetrics.add(EventMetric(event, System.currentTimeMillis() - startTime))

        if (event == CALL_END || event == CALL_FAILED) {
            val eventProps = mutableMapOf<String, Any>()
            eventProps.setValue("URL", url)
            eventProps.setValue("Filtered URL", trimmedUrl)
            eventProps.setValue("Host", host)
            eventProps.setValue("Path", encodedPath)
            eventProps.setValue("Method", method)
            eventProps.setValue("RequestBody Length", requestBodyLength)
            eventProps.setValue("ResponseBody Length", responseBodyLength)
            eventProps.setValue("Success", wasCallSuccessful)
            eventProps.setValue("Status Code", statusCode)
            eventProps.setValue("Error Message", errorMessage)
            eventProps.setValue("Protocol", protocol)

            val overallDuration = findTimeDifferenceBetweenEvents(CALL_START, event)
            eventProps["Duration"] = overallDuration

            val dnsToCallEndDuration = findTimeDifferenceBetweenEvents(DNS_START, event)
            eventProps["DNS Start to Call End Duration"] = dnsToCallEndDuration

            val startToDnsDuration = findTimeDifferenceBetweenEvents(CALL_START, DNS_START)
            eventProps["Call Start To DNS Duration"] = startToDnsDuration

            val dnsDuration = findTimeDifferenceBetweenEvents(DNS_START, DNS_END)
            eventProps["DNS LookUp Duration"] = dnsDuration

            val noOfDnsLookup = findOccurrenceCount(DNS_START)
            eventProps["Total No of DNS LookUp"] = noOfDnsLookup

            val dnsEndToConnectionStartDuration =
                findTimeDifferenceBetweenEvents(DNS_END, CONNECT_START)
            eventProps["DNS End to Connect Start Duration"] = dnsEndToConnectionStartDuration

            val proxyDuration =
                findTimeDifferenceBetweenEvents(PROXY_SELECT_START, PROXY_SELECT_END)
            eventProps["Select Proxy Duration"] = proxyDuration

            val connectionAcquiredDuration =
                findTimeDifferenceBetweenEvents(CONNECT_START, CONNECTION_ACQUIRED)
            eventProps["Connection Acquired Duration"] = connectionAcquiredDuration

            val noOfConnectAttempt = findOccurrenceCount(CONNECT_START)
            eventProps["Total No of Connect Attempt"] = noOfConnectAttempt

            val secureConnectionDuration =
                findTimeDifferenceBetweenEvents(SECURE_CONNECT_START, SECURE_CONNECT_END)
            eventProps["Secure Connection Duration"] = secureConnectionDuration

            val requestHeadersDuration =
                findTimeDifferenceBetweenEvents(REQUEST_HEADERS_START, REQUEST_HEADERS_END)

            eventProps["Request Headers Duration"] = requestHeadersDuration

            val noOfRequestHeaders = findOccurrenceCount(REQUEST_HEADERS_START)
            eventProps["Total No of Request Headers"] = noOfRequestHeaders

            val responseHeadersDuration =
                findTimeDifferenceBetweenEvents(RESPONSE_HEADERS_START, RESPONSE_HEADERS_END)

            eventProps["Response Headers Duration"] = responseHeadersDuration

            val responseBodyDuration =
                findTimeDifferenceBetweenEvents(RESPONSE_BODY_START, RESPONSE_BODY_END)
            eventProps["Response Body Duration"] = responseBodyDuration

            eventProps["Request to Response Duration"] =
                findTimeDifferenceBetweenRequestAndResponse()

            val totalConnectedDuration =
                findTimeDifferenceBetweenEvents(CONNECTION_ACQUIRED, CONNECTION_RELEASED)
            eventProps["Total Connected Duration"] = totalConnectedDuration

            val releasedToEndDuration =
                findTimeDifferenceBetweenEvents(CONNECTION_RELEASED, CALL_END)
            eventProps["Connection Released to Call End Duration"] = releasedToEndDuration

            eventProps["RawEventLogs"] = getRawEventLogs()

            val crumb = Breadcrumb().apply {
                category = "http"
                type = "http"
                data.putAll(eventProps)
            }
            hub.addBreadcrumb(crumb)
        }
    }

    private fun getRawEventLogs(): String {
        var callStartTime = 0L
        val trimToTimeDiffValues = mutableListOf<Pair<String, Long>>()
        eventMetrics.forEach { metric ->
            if (metric.event == CALL_START && callStartTime == 0L) {
                callStartTime = metric.timestamp
                trimToTimeDiffValues.add(metric.event to 0L)
            } else {
                trimToTimeDiffValues.add(metric.event to (metric.timestamp - callStartTime))
            }
        }

        return trimToTimeDiffValues.toString()
    }

    private fun findTimeDifferenceBetweenRequestAndResponse(): Long {
        val startEvent = if (isRequestBodyPresent(method).not()) {
            REQUEST_HEADERS_END
        } else {
            REQUEST_BODY_END
        }
        return findTimeDifferenceBetweenEvents(startEvent, RESPONSE_HEADERS_START)
    }

    private fun isRequestBodyPresent(method: String?): Boolean =
        method == "GET" || method == "DELETE"

    fun findTimeDifferenceBetweenEvents(startEvent: String, endEvent: String): Long {
        var startTime: Long? = null
        var endTime: Long? = null

        eventMetrics.forEach { metric ->
            if (metric.event == startEvent) {
                startTime = metric.timestamp
            } else if (metric.event == endEvent) {
                endTime = metric.timestamp
            }
        }

        if (startTime != null && endTime != null) {
            return endTime!! - startTime!!
        }

        return -1
    }

    fun findOccurrenceCount(event: String): Int {
        return eventMetrics.count { metric -> metric.event == event }
    }

    //region Callback functions

    override fun callStart(call: Call) {
        startTime = System.currentTimeMillis()
        host = call.request().url.host
        encodedPath = call.request().url.encodedPath
        method = call.request().method
        requestBodyLength = call.request().body?.contentLength()
        consumeEvent(CALL_START, call.request().url.toString())
    }

    var proxySelectSpan: ISpan? = null

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        proxySelectSpan = getCurrentRootSpan()?.startChild("http.client", "proxySelect")
        consumeEvent(PROXY_SELECT_START, call.request().url.toString())
    }

    override fun proxySelectEnd(
        call: Call,
        url: HttpUrl,
        proxies: List<Proxy>,
    ) {
        proxySelectSpan?.finish(SpanStatus.OK)
        consumeEvent(PROXY_SELECT_END, call.request().url.toString())
    }

    var dnsSpan: ISpan? = null

    override fun dnsStart(call: Call, domainName: String) {
        dnsSpan = getCurrentRootSpan()?.startChild("http.client", "dns")
        consumeEvent(DNS_START, call.request().url.toString())
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<InetAddress>,
    ) {
        dnsSpan?.finish(SpanStatus.OK)
        consumeEvent(DNS_END, call.request().url.toString())
    }

    var connectSpan: ISpan? = null

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
    ) {
        connectSpan = getCurrentRootSpan()?.startChild("http.client", "connect")
        consumeEvent(CONNECT_START, call.request().url.toString())
    }

    var secureConnectSpan: ISpan? = null

    override fun secureConnectStart(call: Call) {
        secureConnectSpan = getCurrentRootSpan()?.startChild("http.client", "secureConnect")
        consumeEvent(SECURE_CONNECT_START, call.request().url.toString())
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        secureConnectSpan?.finish(SpanStatus.OK)
        consumeEvent(SECURE_CONNECT_END, call.request().url.toString())
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        connectSpan?.finish(SpanStatus.OK)
        consumeEvent(CONNECT_END, call.request().url.toString())
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        connectSpan?.finish(SpanStatus.INTERNAL_ERROR)
        wasCallSuccessful = false
        errorMessage = ioe.message
        failureReason = "Connect Failed"
        consumeEvent(CONNECT_FAILED, call.request().url.toString())
    }

    var connectionAcquireSpan: ISpan? = null

    override fun connectionAcquired(call: Call, connection: Connection) {
        connectionAcquireSpan = getCurrentRootSpan()?.startChild("http.client", "connectionAcquire")
        consumeEvent(CONNECTION_ACQUIRED, call.request().url.toString())
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        connectionAcquireSpan?.finish(SpanStatus.OK)
        consumeEvent(CONNECTION_RELEASED, call.request().url.toString())
    }

    var requestHeadersSpan: ISpan? = null

    override fun requestHeadersStart(call: Call) {
        requestHeadersSpan = getCurrentRootSpan()?.startChild("http.client", "requestHeaders")
        consumeEvent(REQUEST_HEADERS_START, call.request().url.toString())
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        requestHeadersSpan?.finish(SpanStatus.OK)
        consumeEvent(REQUEST_HEADERS_END, call.request().url.toString())
    }

    var requestBodySpan: ISpan? = null

    override fun requestBodyStart(call: Call) {
        requestBodySpan = getCurrentRootSpan()?.startChild("http.client", "requestBody")
        consumeEvent(REQUEST_BODY_START, call.request().url.toString())
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        requestBodySpan?.finish(SpanStatus.OK)
        responseBodyLength = byteCount
        consumeEvent(REQUEST_BODY_END, call.request().url.toString())
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        requestBodySpan?.finish(SpanStatus.INTERNAL_ERROR)
        wasCallSuccessful = false
        errorMessage = ioe.message
        failureReason = "Request Failed"
        consumeEvent(REQUEST_FAILED, call.request().url.toString())
    }

    var responseHeadersSpan: ISpan? = null

    override fun responseHeadersStart(call: Call) {
        responseHeadersSpan = getCurrentRootSpan()?.startChild("http.client", "responseHeaders")
        consumeEvent(RESPONSE_HEADERS_START, call.request().url.toString())
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        responseHeadersSpan?.finish(SpanStatus.OK)
        protocol = response.protocol.name
        statusCode = response.code
        wasCallSuccessful = response.isSuccessful
        consumeEvent(RESPONSE_HEADERS_END, call.request().url.toString())
    }

    var responseBodySpan: ISpan? = null

    override fun responseBodyStart(call: Call) {
        responseBodySpan = getCurrentRootSpan()?.startChild("http.client", "responseBody")
        consumeEvent(RESPONSE_BODY_START, call.request().url.toString())
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        responseBodySpan?.finish(SpanStatus.OK)
        responseBodyLength = byteCount
        consumeEvent(RESPONSE_BODY_END, call.request().url.toString())
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        responseBodySpan?.finish(SpanStatus.INTERNAL_ERROR)
        wasCallSuccessful = false
        errorMessage = ioe.message
        failureReason = "Response Failed"
        consumeEvent(RESPONSE_FAILED, call.request().url.toString())
    }

    override fun callEnd(call: Call) {
        consumeEvent(CALL_END, call.request().url.toString())
    }

    override fun callFailed(call: Call, ioe: IOException) {
        wasCallSuccessful = false
        errorMessage = ioe.message
        consumeEvent(CALL_FAILED, call.request().url.toString())
    }

    //endregion

    //region Helper functions

    fun removeIdsFromUrl(url: String): String {
        val uuid = url.split("/")
            .find { it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) }
        if (uuid?.isNotEmpty() == true) {
            return removeIdsFromUrl(url.replace(uuid, "*"))
        }
        return url
    }

    fun removeParams(url: String): String {
        if (URL(url).query == null) {
            return url
        }
        return url.replace(URL(url).query, "").replace("?", "")
    }

    private fun <String, Any> MutableMap<String, Any>.setValue(key: String, value: Any?) {
        value?.let {
            this[key] = value
        }
    }

    var rootSpan: ISpan? = null
    
    private fun getCurrentRootSpan(): ISpan? {
        if (rootSpan == null) {
            rootSpan = hub.span
        }
        return rootSpan
//        var transaction: ISpan? = null
//        hub.configureScope {
//            transaction = it.transaction
//        }
//        return transaction
    }

    //endregion

    data class EventMetric(val event: String, val timestamp: Long)
}