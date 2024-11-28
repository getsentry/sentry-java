package io.sentry.core

import io.sentry.SentryEvent

/**
 * package-private hack.
 */
internal fun SentryEvent.getExc(): Throwable? {
    return this.throwable
}
