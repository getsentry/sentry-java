package io.sentry.core.util

import io.sentry.core.SentryOptions

fun SentryOptions.noFlushTimeout(): SentryOptions {
    return this.apply {
        flushTimeoutMillis = 0L
    }
}
