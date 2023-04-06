package io.sentry.samples.spring.boot

import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.SentryRuntime

class CustomEventProcessor(val springBootVersion: String) : EventProcessor {

    override fun process(event: SentryEvent, hint: Hint): SentryEvent {
        return event.also { e ->
            e.contexts.setRuntime(
                SentryRuntime().also { runtime ->
                    runtime.version = springBootVersion
                    runtime.name = "Spring Boot Kotlin"
                }
            )
        }
    }
}
