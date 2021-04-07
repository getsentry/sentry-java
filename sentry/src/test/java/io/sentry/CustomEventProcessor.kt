package io.sentry

class CustomEventProcessor : EventProcessor {
    override fun process(event: SentryBaseEvent, hint: Any?): SentryBaseEvent? = null
}
