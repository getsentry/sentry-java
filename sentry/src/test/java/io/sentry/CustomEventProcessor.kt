package io.sentry

class CustomEventProcessor : EventProcessor {
  override fun process(event: SentryEvent, hint: Hint): SentryEvent? = null
}
