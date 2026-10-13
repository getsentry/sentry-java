package io.sentry.apollo5

/**
 * Used for holding an Apollo5 client error, for example. An integration that does not throw when
 * API returns 4xx, 5xx or the `errors` field.
 */
class SentryApollo5ClientException(message: String?) : Exception(message) {
  companion object {
    private const val serialVersionUID = 4312160066430858144L
  }
}
