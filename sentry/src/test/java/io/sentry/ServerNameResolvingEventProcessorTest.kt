package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ServerNameResolvingEventProcessor.HostnameCache
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.awaitility.kotlin.await

class ServerNameResolvingEventProcessorTest {
    class Fixture {
        val getLocalhost = mock<InetAddress>()

        fun getSut(host: String, delay: Long? = null, cacheDuration: Long = 10): ServerNameResolvingEventProcessor {
            whenever(getLocalhost.canonicalHostName).thenAnswer {
                if (delay != null) {
                    Thread.sleep(delay)
                }
                host
            }
            val hostnameCache = HostnameCache(cacheDuration) { getLocalhost }
            return ServerNameResolvingEventProcessor(hostnameCache)
        }
    }

    val fixture = Fixture()

    @Test
    fun `sets servername retrieved from the local address`() {
        val processor = fixture.getSut(host = "aHost")
        val event = SentryEvent()
        processor.process(event, null)
        assertEquals("aHost", event.serverName)
    }

    @Test
    fun `sets servername to null if retrieving takes longer time`() {
        val processor = fixture.getSut(host = "aHost", delay = 2000)
        val event = SentryEvent()
        processor.process(event, null)
        assertNull(event.serverName)
    }

    @Test
    fun `uses cache to retrieve servername for subsequent events`() {
        val processor = fixture.getSut(host = "aHost", cacheDuration = 1000)
        val firstEvent = SentryEvent()
        processor.process(firstEvent, null)
        assertEquals("aHost", firstEvent.serverName)
        val secondEvent = SentryEvent()
        processor.process(secondEvent, null)
        assertEquals("aHost", secondEvent.serverName)
        verify(fixture.getLocalhost, times(1)).canonicalHostName
    }

    @Test
    fun `when cache expires, retrieves new host name from the local address`() {
        val processor = fixture.getSut(host = "aHost")
        val firstEvent = SentryEvent()
        processor.process(firstEvent, null)
        assertEquals("aHost", firstEvent.serverName)

        reset(fixture.getLocalhost)
        whenever(fixture.getLocalhost.canonicalHostName).thenReturn("newHost")

        await.untilAsserted {
            val secondEvent = SentryEvent()
            processor.process(secondEvent, null)
            assertEquals("newHost", secondEvent.serverName)
        }
    }

    @Test
    fun `does not set serverName on events that already have server names`() {
        val processor = fixture.getSut(host = "aHost")
        val event = SentryEvent()
        event.serverName = "eventHost"
        processor.process(event, null)
        assertEquals("eventHost", event.serverName)
    }
}
