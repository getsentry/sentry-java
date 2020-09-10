package io.sentry.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.ISerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import kotlin.test.Test
import kotlin.test.assertTrue

class StdoutTransportTest {
    private class Fixture {
        val serializer = mock<ISerializer>()

        fun getSUT(): ITransport {
            return StdoutTransport(serializer)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `test serializes envelope`() {
        val transport = fixture.getSUT()
        val event = SentryEvent()
        val envelope = SentryEnvelope.fromEvent(fixture.serializer, event, null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertTrue(result.isSuccess)
    }
}
