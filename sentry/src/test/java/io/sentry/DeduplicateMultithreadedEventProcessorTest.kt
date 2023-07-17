package io.sentry

import io.sentry.hints.EventDropReason
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import io.sentry.util.HintUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeduplicateMultithreadedEventProcessorTest {

    class Fixture {

        fun getSut(): DeduplicateMultithreadedEventProcessor {
            return DeduplicateMultithreadedEventProcessor(SentryOptions())
        }

        fun getEvent(
            type: String? = null,
            isHandled: Boolean = true,
            tid: Long? = null
        ): SentryEvent {
            val event = SentryEvent().apply {
                exceptions = listOf(
                    SentryException().apply {
                        this.type = type
                        this.threadId = tid
                        mechanism = Mechanism().apply {
                            this.isHandled = isHandled
                        }
                    }
                )
            }
            return event
        }
    }

    private val fixture = Fixture()

    @Test
    fun `does not drop if not a crash`() {
        val processor = fixture.getSut()
        val processedEvent = processor.process(SentryEvent(), Hint())

        assertNotNull(processedEvent)
    }

    @Test
    fun `does not drop if no type`() {
        val event = fixture.getEvent(isHandled = false)
        val processor = fixture.getSut()
        val processedEvent = processor.process(SentryEvent(), Hint())

        assertNotNull(processedEvent)
    }

    @Test
    fun `does not drop if no tid`() {
        val event = fixture.getEvent(isHandled = false, type = "OutOfMemoryError")
        val processor = fixture.getSut()
        val processedEvent = processor.process(SentryEvent(), Hint())

        assertNotNull(processedEvent)
    }

    @Test
    fun `does not drop if not yet processed`() {
        val event = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
        val processor = fixture.getSut()
        val processedEvent = processor.process(SentryEvent(), Hint())

        assertNotNull(processedEvent)
    }

    @Test
    fun `does not drop if an event of this type was not processed yet`() {
        val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
        val event2 = fixture.getEvent(isHandled = false, type = "RuntimeException", tid = 2)

        val processor = fixture.getSut()

        val processedEvent1 = processor.process(event1, Hint())
        assertNotNull(processedEvent1)

        val processedEvent2 = processor.process(event2, Hint())
        assertNotNull(processedEvent2)
    }

    @Test
    fun `does not drop if an event of this type is from the same thread`() {
        val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
        val event2 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)

        val processor = fixture.getSut()

        val processedEvent1 = processor.process(event1, Hint())
        assertNotNull(processedEvent1)

        val processedEvent2 = processor.process(event2, Hint())
        assertNotNull(processedEvent2)
    }

    @Test
    fun `drops if the same event of this type is from a different thread`() {
        val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
        val event2 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 2)

        val processor = fixture.getSut()

        val processedEvent1 = processor.process(event1, Hint())
        assertNotNull(processedEvent1)

        val processedEvent2 = processor.process(event2, Hint())
        assertNull(processedEvent2)
    }

    @Test
    fun `sets drop reason when dropped`() {
        val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
        val event2 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 2)

        val processor = fixture.getSut()

        val hint = Hint()
        processor.process(event1, hint)
        processor.process(event2, hint)
        assertEquals(
            EventDropReason.MULTITHREADED_DEDUPLICATION,
            HintUtils.getEventDropReason(hint)
        )
    }
}
