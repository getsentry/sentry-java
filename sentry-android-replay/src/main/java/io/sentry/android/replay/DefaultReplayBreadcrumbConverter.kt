package io.sentry.android.replay

import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ReplayBreadcrumbConverter
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryOptions.BeforeBreadcrumbCallback
import io.sentry.SpanDataConvention
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebEvent
import io.sentry.rrweb.RRWebSpanEvent
import io.sentry.util.network.NetworkRequestData
import kotlin.LazyThreadSafetyMode.NONE

public open class DefaultReplayBreadcrumbConverter(
  private val userBeforeBreadcrumbCallback: BeforeBreadcrumbCallback? = null
) : ReplayBreadcrumbConverter, SentryOptions.BeforeBreadcrumbCallback {
  internal companion object {
    private val snakecasePattern by lazy(NONE) { "_[a-z]".toRegex() }
    private val supportedNetworkData =
      HashSet<String>().apply {
        add("status_code")
        add("method")
        add("response_content_length")
        add("request_content_length")
        add("http.response_content_length")
        add("http.request_content_length")
      }
  }

  private var lastConnectivityState: String? = null
  private val httpBreadcrumbData = mutableMapOf<Breadcrumb, NetworkRequestData>()

  override fun convert(breadcrumb: Breadcrumb): RRWebEvent? {
    var breadcrumbMessage: String? = null
    var breadcrumbCategory: String?
    var breadcrumbLevel: SentryLevel? = null
    val breadcrumbData = mutableMapOf<String, Any?>()
    when {
      breadcrumb.category == "http" -> {
        return if (breadcrumb.isValidForRRWebSpan()) breadcrumb.toRRWebSpanEvent() else null
      }

      breadcrumb.type == "navigation" && breadcrumb.category == "app.lifecycle" -> {
        breadcrumbCategory = "app.${breadcrumb.data["state"]}"
      }

      breadcrumb.type == "navigation" && breadcrumb.category == "device.orientation" -> {
        breadcrumbCategory = breadcrumb.category!!
        val position = breadcrumb.data["position"]
        if (position == "landscape" || position == "portrait") {
          breadcrumbData["position"] = position
        } else {
          return null
        }
      }

      breadcrumb.type == "navigation" -> {
        breadcrumbCategory = "navigation"
        breadcrumbData["to"] =
          when {
            breadcrumb.data["state"] == "resumed" ->
              (breadcrumb.data["screen"] as? String)?.substringAfterLast('.')
            "to" in breadcrumb.data -> breadcrumb.data["to"] as? String
            else -> null
          } ?: return null
      }

      breadcrumb.category == "ui.click" -> {
        breadcrumbCategory = "ui.tap"
        breadcrumbMessage =
          (breadcrumb.data["view.id"]
            ?: breadcrumb.data["view.tag"]
            ?: breadcrumb.data["view.class"])
            as? String ?: return null
        breadcrumbData.putAll(breadcrumb.data)
      }

      breadcrumb.type == "system" && breadcrumb.category == "network.event" -> {
        breadcrumbCategory = "device.connectivity"
        breadcrumbData["state"] =
          when {
            breadcrumb.data["action"] == "NETWORK_LOST" -> "offline"
            "network_type" in breadcrumb.data ->
              if (!(breadcrumb.data["network_type"] as? String).isNullOrEmpty()) {
                breadcrumb.data["network_type"]
              } else {
                return null
              }

            else -> return null
          }

        if (lastConnectivityState == breadcrumbData["state"]) {
          // debounce same state
          return null
        }

        lastConnectivityState = breadcrumbData["state"] as? String
      }

      breadcrumb.data["action"] == "BATTERY_CHANGED" -> {
        breadcrumbCategory = "device.battery"
        breadcrumbData.putAll(breadcrumb.data.filterKeys { it == "level" || it == "charging" })
      }

      else -> {
        breadcrumbCategory = breadcrumb.category
        breadcrumbMessage = breadcrumb.message
        breadcrumbLevel = breadcrumb.level
        breadcrumbData.putAll(breadcrumb.data)
      }
    }
    return if (!breadcrumbCategory.isNullOrEmpty()) {
      RRWebBreadcrumbEvent().apply {
        timestamp = breadcrumb.timestamp.time
        breadcrumbTimestamp = breadcrumb.timestamp.time / 1000.0
        breadcrumbType = "default"
        category = breadcrumbCategory
        message = breadcrumbMessage
        level = breadcrumbLevel
        data = breadcrumbData
      }
    } else {
      null
    }
  }

  /**
   * By default, ReplayIntegration provides its own BeforeBreadcrumbCallback,
   * delegating to user-provided callback (if exists).
   */
  override fun execute(breadcrumb: Breadcrumb, hint: Hint): Breadcrumb? {
    Log.d("SentryNetwork", "SentryNetwork: BeforeBreadcrumbCallback - Hint: $hint, Breadcrumb: $breadcrumb")
    return userBeforeBreadcrumbCallback?.let {
      it.execute(breadcrumb, hint)?.also { processedBreadcrumb ->
        extractNetworkRequestDataFromHint(processedBreadcrumb, hint)?.let { networkData ->
          httpBreadcrumbData[processedBreadcrumb] = networkData
        }
      }
    } ?: run {
      // No user callback - store hint and return original breadcrumb
      extractNetworkRequestDataFromHint(breadcrumb, hint)?.let { networkData ->
        httpBreadcrumbData[breadcrumb] = networkData
      }
      breadcrumb
    }
  }

  private fun extractNetworkRequestDataFromHint(breadcrumb: Breadcrumb, breadcrumbHint: Hint): NetworkRequestData? {
    if (breadcrumb.type != "http" && breadcrumb.category != "http") {
      return null
    }

    // First try to get the structured network data from the hint
    val networkDetails = breadcrumbHint.get("replay:networkDetails") as? NetworkRequestData
    if (networkDetails != null) {
      Log.d("SentryNetwork", "SentryNetwork: Found structured NetworkRequestData in hint: $networkDetails")
      return networkDetails
    }

    Log.d("SentryNetwork", "SentryNetwork: No structured NetworkRequestData found on hint")
    return null
  }

  private fun Breadcrumb.isValidForRRWebSpan(): Boolean {
    val url = data["url"] as? String
    val hasStartTimestamp = SpanDataConvention.HTTP_START_TIMESTAMP in data
    val hasEndTimestamp = SpanDataConvention.HTTP_END_TIMESTAMP in data

    val urlValid = !url.isNullOrEmpty()
    val isValid = urlValid && hasStartTimestamp && hasEndTimestamp

    val reasons = mutableListOf<String>()
    if (!urlValid) reasons.add("missing or empty URL")
    if (!hasStartTimestamp) reasons.add("missing start timestamp")
    if (!hasEndTimestamp) reasons.add("missing end timestamp")

    Log.d("SentryReplay", "Breadcrumb RRWeb span validation: ${if (isValid) "VALID" else "INVALID"}" +
      if (!isValid) " (${reasons.joinToString(", ")})" else "" +
      " - URL: ${url ?: "null"}, Category: ${category}")

    return isValid
  }

  private fun String.snakeToCamelCase(): String =
    replace(snakecasePattern) { it.value.last().toString().uppercase() }

  private fun Breadcrumb.toRRWebSpanEvent(): RRWebSpanEvent {
    val breadcrumb = this
    val httpStartTimestamp = breadcrumb.data[SpanDataConvention.HTTP_START_TIMESTAMP]
    val httpEndTimestamp = breadcrumb.data[SpanDataConvention.HTTP_END_TIMESTAMP]

    // Get the NetworkRequestData if available
    val networkRequestData = httpBreadcrumbData[breadcrumb]

    Log.d("SentryNetwork", "SentryNetwork: convert(breadcrumb=${breadcrumb.type}) httpBreadcrumbData map size: ${httpBreadcrumbData.size}, " +
      "contains current breadcrumb: ${httpBreadcrumbData.containsKey(breadcrumb)}, " +
      "network data for current: ${httpBreadcrumbData[breadcrumb]}")

    return RRWebSpanEvent().apply {
      timestamp = breadcrumb.timestamp.time
      op = "resource.http"
      description = breadcrumb.data["url"] as String
      // can be double if it was serialized to disk
      startTimestamp =
        if (httpStartTimestamp is Double) {
          httpStartTimestamp / 1000.0
        } else {
          (httpStartTimestamp as Long) / 1000.0
        }
      endTimestamp =
        if (httpEndTimestamp is Double) {
          httpEndTimestamp / 1000.0
        } else {
          (httpEndTimestamp as Long) / 1000.0
        }

      val breadcrumbData = mutableMapOf<String, Any?>()

      // Add data from NetworkRequestData if available
      if (networkRequestData != null) {
        networkRequestData.method?.let { breadcrumbData["method"] = it }
        networkRequestData.statusCode?.let { breadcrumbData["statusCode"] = it }
        networkRequestData.requestBodySize?.let { breadcrumbData["requestBodySize"] = it }
        networkRequestData.responseBodySize?.let { breadcrumbData["responseBodySize"] = it }

        // Add request and response data if available
        networkRequestData.request?.let { request ->
          val requestData = mutableMapOf<String, Any?>()
          request.size?.let { requestData["size"] = it }
          request.body?.let { requestData["body"] = it }
          if (request.headers.isNotEmpty()) {
            requestData["headers"] = request.headers
          }
          if (requestData.isNotEmpty()) {
            breadcrumbData["request"] = requestData
          }
        }

        networkRequestData.response?.let { response ->
          val responseData = mutableMapOf<String, Any?>()
          response.size?.let { responseData["size"] = it }
          response.body?.let { responseData["body"] = it }
          if (response.headers.isNotEmpty()) {
            responseData["headers"] = response.headers
          }
          if (responseData.isNotEmpty()) {
            breadcrumbData["response"] = responseData
          }
        }
      }
      // Original breadcrumb data processing
      // TODO: Remove if superceded by more detailed data (above).
      for ((key, value) in breadcrumb.data) {
        if (key in supportedNetworkData) {
          breadcrumbData[
            key.replace("content_length", "body_size").substringAfter(".").snakeToCamelCase(),
          ] = value
        }
      }


      data = breadcrumbData
    }
  }
}
