package io.sentry

/**
 * package-private hack.
 */
fun SentryEvent.getExc(): Throwable? {
    return this.throwable
}
