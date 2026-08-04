package io.sentry.samples.android.navigation

internal enum class RouteActivationAction(val label: String, val tagName: String) {
  NONE("None", "none"),
  HTTP_REQUEST("HTTP request", "http_request"),
  MANUAL_CHILD_SPAN("Manual child span", "manual_child_span"),
}
