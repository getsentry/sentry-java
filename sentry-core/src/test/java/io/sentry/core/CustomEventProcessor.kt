package io.sentry.core

class CustomEventProcessor : EventProcessor {
    override fun process(event: SentryEvent, hint: Any?): SentryEvent? = null
}
