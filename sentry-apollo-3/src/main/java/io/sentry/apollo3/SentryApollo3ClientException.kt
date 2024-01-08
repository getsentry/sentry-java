package io.sentry.apollo3

/**
 * Used for holding an Apollo3 client error, for example. An integration that does not throw when API
 * returns 4xx, 5xx or the `errors` field.
 */
class SentryApollo3ClientException(message: String?) : Exception(message) {
    companion object {
        private const val serialVersionUID = 4312120066430858144L
    }
}
