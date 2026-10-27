package io.sentry.samples.android.navigation.common

/** Optional work navigation sample routes can perform after they become active. */
internal enum class RouteWorkOption(val label: String, val tagName: String) {
  HTTP_REQUEST("HTTP request", "http_request"),
  MANUAL_CHILD_SPAN("Manual child span", "manual_child_span"),
}
