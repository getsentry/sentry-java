package io.sentry

import io.sentry.hints.Hint

class CustomEventProcessor : EventProcessor {
    override fun process(event: SentryEvent, hint: Hint): SentryEvent? = null
}
