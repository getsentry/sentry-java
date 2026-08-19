package io.sentry.android.buddy.model

import java.net.URLEncoder

internal data class BuddySentryUiLinks(
  val baseUrl: String? = null,
  val organizationSlug: String? = null,
  val projectId: String? = null,
) {
  fun linkFor(recording: BuddyFlowRecording): String? {
    val baseUrl = resolvedBaseUrl() ?: return null
    val projectId = projectId?.takeIf { it.isNotBlank() } ?: return null
    return traceLink(
      baseUrl = baseUrl,
      projectId = projectId,
      traceId = recording.sentry.traceId ?: return null,
      spanId = recording.sentry.spanId,
    )
  }

  fun linkFor(item: BuddyLiveFeedItem): String? {
    val baseUrl = resolvedBaseUrl() ?: return null
    val projectId = projectId?.takeIf { it.isNotBlank() } ?: return null
    return when (item.category) {
      BuddyLiveFeedItem.Category.ERROR ->
        item.timelineItem.data.stringValue(DATA_EVENT_ID)?.let { eventId ->
          "$baseUrl/issues/?project=${projectId.urlEncode()}&query=${"event.id:$eventId".urlEncode()}"
        }

      BuddyLiveFeedItem.Category.SLOW_SPAN,
      BuddyLiveFeedItem.Category.FAILED_SPAN -> traceLink(item, baseUrl, projectId)

      BuddyLiveFeedItem.Category.FAILED_HTTP -> traceLink(item, baseUrl, projectId)
      BuddyLiveFeedItem.Category.SCREEN,
      BuddyLiveFeedItem.Category.STEP -> null
    }
  }

  private fun traceLink(item: BuddyLiveFeedItem, baseUrl: String, projectId: String): String? {
    val traceId = item.timelineItem.data.stringValue(DATA_TRACE_ID) ?: return null
    val spanId = item.timelineItem.data.stringValue(DATA_SPAN_ID)
    return traceLink(baseUrl, projectId, traceId, spanId)
  }

  private fun traceLink(
    baseUrl: String,
    projectId: String,
    traceId: String,
    spanId: String?,
  ): String {
    return buildString {
      append(baseUrl)
      append("/performance/trace/")
      append(traceId.urlEncode())
      append("/?project=")
      append(projectId.urlEncode())
      if (!spanId.isNullOrBlank()) {
        append("&span=")
        append(spanId.urlEncode())
      }
    }
  }

  private fun resolvedBaseUrl(): String? {
    val explicitBaseUrl = baseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
    if (explicitBaseUrl != null) {
      return explicitBaseUrl
    }
    val organization = organizationSlug?.takeIf { it.isNotBlank() } ?: return null
    return "https://$organization.sentry.io"
  }

  private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

  private companion object {
    private const val DATA_EVENT_ID = "event_id"
    private const val DATA_TRACE_ID = "trace_id"
    private const val DATA_SPAN_ID = "span_id"
  }
}

private fun Map<String, Any?>.stringValue(key: String): String? = this[key]?.toString()
