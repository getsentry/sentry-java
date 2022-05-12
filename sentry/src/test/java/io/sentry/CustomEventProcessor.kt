package io.sentry

import io.sentry.hints.Hints

class CustomEventProcessor : EventProcessor {
    override fun process(event: SentryEvent, hints: Hints): SentryEvent? = null
}
