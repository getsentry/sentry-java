package io.sentry.transport

import io.sentry.ISerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test

class StdoutTransportTest {
    private class Fixture {
        val serializer = mock<ISerializer>()

        fun getSUT(): ITransport = StdoutTransport(serializer)
    }

    private val fixture = Fixture()

    @Test
    fun `test serializes envelope`() {
        val transport = fixture.getSUT()
        val event = SentryEvent()
        val envelope = SentryEnvelope.from(fixture.serializer, event, null)

        transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
    }
}
