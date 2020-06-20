package io.sentry.core.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.ISerializer
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import kotlin.test.Test
import kotlin.test.assertTrue

class StdoutTransportTest {
    private class Fixture {
        val serializer = mock<ISerializer>()

        fun getSUT(): ITransport {
            val options = SentryOptions()
            options.setSerializer(serializer)

            return StdoutTransport(serializer)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `test serializes event`() {
        val transport = fixture.getSUT()
        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertTrue(result.isSuccess)
    }
}
