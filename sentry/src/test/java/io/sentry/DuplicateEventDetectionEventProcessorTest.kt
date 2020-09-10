package io.sentry

import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DuplicateEventDetectionEventProcessorTest {

    val processor = DuplicateEventDetectionEventProcessor(SentryOptions())

    @Test
    fun `does not drop event if no previous event with same exception was processed`() {
        processor.process(SentryEvent(), null)

        val result = processor.process(SentryEvent(RuntimeException()), null)

        assertNotNull(result)
    }

    @Test
    fun `drops event with the same exception`() {
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(event, null)
        assertNull(result)
    }

    @Test
    fun `drops event with mechanism exception having an exception that has already been processed`() {
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(SentryEvent(ExceptionMechanismException(Mechanism(), event.throwable, null)), null)
        assertNull(result)
    }

    @Test
    fun `drops event with exception that has already been processed with event with mechanism exception`() {
        val sentryEvent = SentryEvent(ExceptionMechanismException(Mechanism(), RuntimeException(), null))
        processor.process(sentryEvent, null)

        val result = processor.process(SentryEvent((sentryEvent.throwable as ExceptionMechanismException).throwable), null)

        assertNull(result)
    }

    @Test
    fun `drops event with the cause equal to exception in already processed event`() {
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(SentryEvent(RuntimeException(event.throwable)), null)

        assertNull(result)
    }

    @Test
    fun `drops event with any of the causes has been already processed`() {
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(SentryEvent(RuntimeException(RuntimeException(event.throwable))), null)

        assertNull(result)
    }
}
