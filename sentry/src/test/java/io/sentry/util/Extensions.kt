package io.sentry.util

import io.sentry.SentryOptions

fun SentryOptions.noFlushTimeout(): SentryOptions {
    return this.apply {
        flushTimeoutMillis = 0L
    }
}
