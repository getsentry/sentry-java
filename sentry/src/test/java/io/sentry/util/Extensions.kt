package io.sentry.util

import io.sentry.SentryOptions

fun SentryOptions.noFlushTimeout(): SentryOptions =
    this.apply {
        flushTimeoutMillis = 0L
    }
