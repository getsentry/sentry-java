package io.sentry

import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DuplicateEventDetectionEventProcessorTest {
    class Fixture {
        fun getSut(enableDeduplication: Boolean? = null): DuplicateEventDetectionEventProcessor {
            val options =
                SentryOptions().apply {
                    if (enableDeduplication != null) {
                        this.setEnableDeduplication(enableDeduplication)
                    }
                }
            return DuplicateEventDetectionEventProcessor(options)
        }
    }

    var fixture = Fixture()

    @Test
    fun `does not drop event if no previous event with same exception was processed`() {
        val processor = fixture.getSut()
        processor.process(SentryEvent(), Hint())

        val result =
            processor.process(
                SentryEvent(RuntimeException()),
                Hint(),
            )

        assertNotNull(result)
    }

    @Test
    fun `drops event with the same exception`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, Hint())

        val result = processor.process(event, Hint())
        assertNull(result)
    }

    @Test
    fun `drops event with mechanism exception having an exception that has already been processed`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, Hint())

        val result =
            processor.process(
                SentryEvent(ExceptionMechanismException(Mechanism(), event.throwable!!, Thread.currentThread())),
                Hint(),
            )
        assertNull(result)
    }

    @Test
    fun `drops event with exception that has already been processed with event with mechanism exception`() {
        val processor = fixture.getSut()
        val sentryEvent = SentryEvent(ExceptionMechanismException(Mechanism(), RuntimeException(), Thread.currentThread()))
        processor.process(sentryEvent, Hint())

        val result =
            processor.process(
                SentryEvent((sentryEvent.throwable as ExceptionMechanismException).throwable),
                Hint(),
            )

        assertNull(result)
    }

    @Test
    fun `drops event with the cause equal to exception in already processed event`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, Hint())

        val result =
            processor.process(
                SentryEvent(RuntimeException(event.throwable)),
                Hint(),
            )

        assertNull(result)
    }

    @Test
    fun `drops event with any of the causes has been already processed`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, Hint())

        val result =
            processor.process(
                SentryEvent(RuntimeException(RuntimeException(event.throwable))),
                Hint(),
            )

        assertNull(result)
    }

    @Test
    fun `does not deduplicate is deduplication is disabled`() {
        val processor = fixture.getSut(enableDeduplication = false)
        val event = SentryEvent(RuntimeException())
        assertNotNull(processor.process(event, Hint()))
        assertNotNull(processor.process(event, Hint()))
    }
}
