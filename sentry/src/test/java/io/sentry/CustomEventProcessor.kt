package io.sentry

class CustomEventProcessor : EventProcessor {
    override fun process(event: SentryEvent, hint: Map<String, Any?>?): SentryEvent? = null
}
