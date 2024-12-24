package io.sentry.android.replay

import io.sentry.Breadcrumb
import io.sentry.ReplayBreadcrumbConverter
import io.sentry.SentryLevel
import io.sentry.SpanDataConvention
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebEvent
import io.sentry.rrweb.RRWebSpanEvent
import kotlin.LazyThreadSafetyMode.NONE

public open class DefaultReplayBreadcrumbConverter : ReplayBreadcrumbConverter {
    internal companion object {
        private val snakecasePattern by lazy(NONE) { "_[a-z]".toRegex() }
        private val supportedNetworkData = HashSet<String>().apply {
            add("status_code")
            add("method")
            add("response_content_length")
            add("request_content_length")
            add("http.response_content_length")
            add("http.request_content_length")
        }
    }

    private var lastConnectivityState: String? = null

    override fun convert(breadcrumb: Breadcrumb): RRWebEvent? {
        var breadcrumbMessage: String? = null
        var breadcrumbCategory: String? = null
        var breadcrumbLevel: SentryLevel? = null
        val breadcrumbData = mutableMapOf<String, Any?>()
        when {
            breadcrumb.category == "http" -> {
                return if (breadcrumb.isValidForRRWebSpan()) breadcrumb.toRRWebSpanEvent() else null
            }

            breadcrumb.type == "navigation" &&
                breadcrumb.category == "app.lifecycle" -> {
                breadcrumbCategory = "app.${breadcrumb.data["state"]}"
            }

            breadcrumb.type == "navigation" &&
                breadcrumb.category == "device.orientation" -> {
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
                breadcrumbData["to"] = when {
                    breadcrumb.data["state"] == "resumed" -> (breadcrumb.data["screen"] as? String)?.substringAfterLast('.')
                    "to" in breadcrumb.data -> breadcrumb.data["to"] as? String
                    else -> null
                } ?: return null
            }

            breadcrumb.category == "ui.click" -> {
                breadcrumbCategory = "ui.tap"
                breadcrumbMessage = (
                    breadcrumb.data["view.id"]
                        ?: breadcrumb.data["view.tag"]
                        ?: breadcrumb.data["view.class"]
                    ) as? String ?: return null
                breadcrumbData.putAll(breadcrumb.data)
            }

            breadcrumb.type == "system" && breadcrumb.category == "network.event" -> {
                breadcrumbCategory = "device.connectivity"
                breadcrumbData["state"] = when {
                    breadcrumb.data["action"] == "NETWORK_LOST" -> "offline"
                    "network_type" in breadcrumb.data -> if (!(breadcrumb.data["network_type"] as? String).isNullOrEmpty()) {
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
                breadcrumbData.putAll(
                    breadcrumb.data.filterKeys { it == "level" || it == "charging" }
                )
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

    private fun Breadcrumb.isValidForRRWebSpan(): Boolean {
        return !(data["url"] as? String).isNullOrEmpty() &&
            SpanDataConvention.HTTP_START_TIMESTAMP in data &&
            SpanDataConvention.HTTP_END_TIMESTAMP in data
    }

    private fun String.snakeToCamelCase(): String {
        return replace(snakecasePattern) {
            it.value.last().toString().uppercase()
        }
    }

    private fun Breadcrumb.toRRWebSpanEvent(): RRWebSpanEvent {
        val breadcrumb = this
        val httpStartTimestamp = breadcrumb.data[SpanDataConvention.HTTP_START_TIMESTAMP]
        val httpEndTimestamp = breadcrumb.data[SpanDataConvention.HTTP_END_TIMESTAMP]
        return RRWebSpanEvent().apply {
            timestamp = breadcrumb.timestamp.time
            op = "resource.http"
            description = breadcrumb.data["url"] as String
            // can be double if it was serialized to disk
            startTimestamp = if (httpStartTimestamp is Double) {
                httpStartTimestamp / 1000.0
            } else {
                (httpStartTimestamp as Long) / 1000.0
            }
            endTimestamp = if (httpEndTimestamp is Double) {
                httpEndTimestamp / 1000.0
            } else {
                (httpEndTimestamp as Long) / 1000.0
            }

            val breadcrumbData = mutableMapOf<String, Any?>()
            for ((key, value) in breadcrumb.data) {
                if (key in supportedNetworkData) {
                    breadcrumbData[
                        key
                            .replace("content_length", "body_size")
                            .substringAfter(".")
                            .snakeToCamelCase()
                    ] = value
                }
            }
            data = breadcrumbData
        }
    }
}
