package io.sentry.samples.android.navigation

internal enum class RouteActivationAction(val label: String, val tagName: String) {
  NONE("None", "none"),
  HTTP_REQUEST("HTTP request", "http_request"),
  MANUAL_CHILD_SPAN_ASYNC("Manual child span (async)", "manual_child_span_async"),
  MANUAL_CHILD_SPAN_SYNC("Manual child span (sync)", "manual_child_span_sync"),
}
