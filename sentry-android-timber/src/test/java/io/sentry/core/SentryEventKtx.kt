package io.sentry.core

/**
 * package-private hack.
 */
fun SentryEvent.getExc(): Throwable? {
    return this.throwable
}
