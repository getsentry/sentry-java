package io.sentry.android.okhttp

class SentryHttpClientError(override val message: String) : RuntimeException(message) {

}
