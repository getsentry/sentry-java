package io.sentry

import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DuplicateEventDetectionEventProcessorTest {

    class Fixture {
        fun getSut(bufferSize: Int? = null): DuplicateEventDetectionEventProcessor {
            return if (bufferSize != null) {
                DuplicateEventDetectionEventProcessor(SentryOptions(), bufferSize)
            } else {
                DuplicateEventDetectionEventProcessor(SentryOptions())
            }
        }
    }

    var fixture = Fixture()

    @Test
    fun `does not drop event if no previous event with same exception was processed`() {
        val processor = fixture.getSut()
        processor.process(SentryEvent(), null)

        val result = processor.process(SentryEvent(RuntimeException()), null)

        assertNotNull(result)
    }

    @Test
    fun `drops event with the same exception`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(event, null)
        assertNull(result)
    }

    @Test
    fun `drops event with mechanism exception having an exception that has already been processed`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(SentryEvent(ExceptionMechanismException(Mechanism(), event.throwable!!, Thread.currentThread())), null)
        assertNull(result)
    }

    @Test
    fun `drops event with exception that has already been processed with event with mechanism exception`() {
        val processor = fixture.getSut()
        val sentryEvent = SentryEvent(ExceptionMechanismException(Mechanism(), RuntimeException(), Thread.currentThread()))
        processor.process(sentryEvent, null)

        val result = processor.process(SentryEvent((sentryEvent.throwable as ExceptionMechanismException).throwable), null)

        assertNull(result)
    }

    @Test
    fun `drops event with the cause equal to exception in already processed event`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(SentryEvent(RuntimeException(event.throwable)), null)

        assertNull(result)
    }

    @Test
    fun `drops event with any of the causes has been already processed`() {
        val processor = fixture.getSut()
        val event = SentryEvent(RuntimeException())
        processor.process(event, null)

        val result = processor.process(SentryEvent(RuntimeException(RuntimeException(event.throwable))), null)

        assertNull(result)
    }

    @Test
    fun `does not keep in memory more items than the buffer size`() {
        val processor = fixture.getSut(50)
        for (i in 1..100) {
            val event = SentryEvent(RuntimeException())
            processor.process(event, null)
        }
        assertEquals(50, processor.size())
    }
}
