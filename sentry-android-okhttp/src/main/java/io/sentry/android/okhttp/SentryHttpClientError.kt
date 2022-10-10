package io.sentry.android.okhttp

// @ApiStatus.Internal
class SentryHttpClientError(override val message: String) : RuntimeException(message)
