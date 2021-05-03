package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Cached
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import java.lang.RuntimeException
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.awaitility.kotlin.await

class MainEventProcessorTest {
    class Fixture {
        private val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            release = "release"
            dist = "dist"
            sdkVersion = SdkVersion("test", "1.2.3")
        }
        val getLocalhost = mock<InetAddress>()
        val sentryTracer = SentryTracer(TransactionContext("", ""), mock())

        fun getSut(attachThreads: Boolean = true, attachStackTrace: Boolean = true, environment: String? = "environment", tags: Map<String, String> = emptyMap(), sendDefaultPii: Boolean? = null, serverName: String? = "server", host: String? = null, resolveHostDelay: Long? = null, hostnameCacheDuration: Long = 10): MainEventProcessor {
            sentryOptions.isAttachThreads = attachThreads
            sentryOptions.isAttachStacktrace = attachStackTrace
            sentryOptions.environment = environment
            sentryOptions.serverName = serverName
            if (sendDefaultPii != null) {
                sentryOptions.isSendDefaultPii = sendDefaultPii
            }
            tags.forEach { sentryOptions.setTag(it.key, it.value) }
            whenever(getLocalhost.canonicalHostName).thenAnswer {
                if (resolveHostDelay != null) {
                    Thread.sleep(resolveHostDelay)
                }
                host
            }
            val hostnameCache = HostnameCache(hostnameCacheDuration) { getLocalhost }
            return MainEventProcessor(sentryOptions, hostnameCache)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flagged, mechanism added`() {
        val sut = fixture.getSut()

        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertNotNull(event.exceptions) {
            assertSame(crashedThread.id, it.first().threadId)
            assertNotNull(it.first().mechanism) {
                assertFalse(it.isHandled!!)
            }
        }
        assertNotNull(event.threads) {
            assertTrue(it.first { t -> t.id == crashedThread.id }.isCrashed == true)
        }
    }

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flagged, even if its not the current thread`() {
        val sut = fixture.getSut()

        val crashedThread = Thread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertTrue(event.threads!!.any { it.isCrashed == true })
    }

    @Test
    fun `When hint is not Cached, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads!!.first { t -> t.id == crashedThread.id }.isCrashed == true)
    }

    @Test
    fun `When hint is ApplyScopeData, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, mock<ApplyScopeData>())

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads!!.first { t -> t.id == crashedThread.id }.isCrashed == true)
    }

    @Test
    fun `data should be applied only if event doesn't have them`() {
        val sut = fixture.getSut()
        var event = generateCrashedEvent()
        event.dist = "eventDist"
        event.environment = "eventEnvironment"
        event.release = "eventRelease"
        event.serverName = "eventServerName"

        event = sut.process(event, null)

        assertEquals("eventRelease", event.release)
        assertEquals("eventEnvironment", event.environment)
        assertEquals("eventDist", event.dist)
        assertEquals("eventServerName", event.serverName)
    }

    @Test
    fun `When hint is Cached, data should not be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, CachedEvent())

        assertNull(event.release)
        assertNull(event.environment)
        assertNull(event.dist)
        assertNull(event.serverName)
        assertNull(event.threads)
    }

    @Test
    fun `When hint is Cached but also ApplyScopeData, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, mock<CustomCachedApplyScopeDataHint>())

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads!!.first { t -> t.id == crashedThread.id }.isCrashed == true)
    }

    @Test
    fun `when attach threads is disabled, threads should not be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = false)

        var event = SentryEvent()
        event = sut.process(event, null)

        assertNull(event.threads)
    }

    @Test
    fun `when attach threads is enabled, threads should be set`() {
        val sut = fixture.getSut()

        var event = SentryEvent()
        event = sut.process(event, null)

        assertNotNull(event.threads)
    }

    @Test
    fun `when attach threads is disabled, but attach stacktrace is enabled, current thread should be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = true)

        var event = SentryEvent()
        event = sut.process(event, null)

        assertEquals(1, event.threads!!.count())
    }

    @Test
    fun `when attach threads is disabled, but attach stacktrace is enabled and has exceptions, threads should not be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = true)

        var event = SentryEvent(RuntimeException("error"))
        event = sut.process(event, null)

        assertNull(event.threads)
    }

    @Test
    fun `sets sdkVersion in the event`() {
        val sut = fixture.getSut()
        val event = SentryEvent()
        sut.process(event, null)
        assertNotNull(event.sdk)
        assertEquals(event.sdk!!.name, "test")
        assertEquals(event.sdk!!.version, "1.2.3")
    }

    @Test
    fun `when event and SentryOptions do not have environment set, sets "production" as environment`() {
        val sut = fixture.getSut(environment = null)
        val event = SentryEvent()
        sut.process(event, null)
        assertEquals("production", event.environment)
    }

    @Test
    fun `when event does not have ip address set and sendDefaultPii is set to true, sets "{{auto}}" as the ip address`() {
        val sut = fixture.getSut(sendDefaultPii = true)
        val event = SentryEvent()
        sut.process(event, null)
        assertNotNull(event.user) {
            assertEquals("{{auto}}", it.ipAddress)
        }
    }

    @Test
    fun `when event has ip address set and sendDefaultPii is set to true, keeps original ip address`() {
        val sut = fixture.getSut(sendDefaultPii = true)
        val event = SentryEvent()
        event.user = User().apply {
            ipAddress = "192.168.0.1"
        }
        sut.process(event, null)
        assertNotNull(event.user) {
            assertEquals("192.168.0.1", it.ipAddress)
        }
    }

    @Test
    fun `when event does not have ip address set and sendDefaultPii is set to false, does not set ip address`() {
        val sut = fixture.getSut(sendDefaultPii = false)
        val event = SentryEvent()
        event.user = User()
        sut.process(event, null)
        assertNotNull(event.user) {
            assertNull(it.ipAddress)
        }
    }

    @Test
    fun `when event has environment set, does not overwrite environment`() {
        val sut = fixture.getSut(environment = null)
        val event = SentryEvent()
        event.environment = "staging"
        sut.process(event, null)
        assertEquals("staging", event.environment)
    }

    @Test
    fun `when event does not have environment set and SentryOptions have environment set, uses environment from SentryOptions`() {
        val sut = fixture.getSut(environment = "custom")
        val event = SentryEvent()
        sut.process(event, null)
        assertEquals("custom", event.environment)
    }

    @Test
    fun `sets tags from SentryOptions`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1", "tag2" to "value2"))
        val event = SentryEvent()
        sut.process(event, null)
        assertEquals("value1", event.tags!!["tag1"])
        assertEquals("value2", event.tags!!["tag2"])
    }

    @Test
    fun `when event has a tag set with the same name as SentryOptions tags, the tag value from the event is retained`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1", "tag2" to "value2"))
        val event = SentryEvent()
        event.setTag("tag2", "event-tag-value")
        sut.process(event, null)
        assertEquals("value1", event.tags!!["tag1"])
        assertEquals("event-tag-value", event.tags!!["tag2"])
    }

    @Test
    fun `sets servername retrieved from the local address`() {
        val processor = fixture.getSut(serverName = null, host = "aHost")
        val event = SentryEvent()
        processor.process(event, null)
        assertEquals("aHost", event.serverName)
    }

    @Test
    fun `sets servername to null if retrieving takes longer time`() {
        val processor = fixture.getSut(serverName = null, host = "aHost", resolveHostDelay = 2000)
        val event = SentryEvent()
        processor.process(event, null)
        assertNull(event.serverName)
    }

    @Test
    fun `uses cache to retrieve servername for subsequent events`() {
        val processor = fixture.getSut(serverName = null, host = "aHost", hostnameCacheDuration = 1000)
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
        val processor = fixture.getSut(serverName = null, host = "aHost")
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
        val processor = fixture.getSut(serverName = null, host = "aHost")
        val event = SentryEvent()
        event.serverName = "eventHost"
        processor.process(event, null)
        assertEquals("eventHost", event.serverName)
    }

    @Test
    fun `does not set serverName on events if serverName is set on SentryOptions`() {
        val processor = fixture.getSut(serverName = "optionsHost", host = "aHost")
        val event = SentryEvent()
        processor.process(event, null)
        assertEquals("optionsHost", event.serverName)
    }

    @Test
    fun `Server name is set on transaction`() {
        val processor = fixture.getSut(serverName = "optionsHost")

        var transaction = SentryTransaction(fixture.sentryTracer)
        transaction = processor.process(transaction, null)

        assertEquals("optionsHost", transaction.serverName)
    }

    @Test
    fun `Dist is set on transaction`() {
        val processor = fixture.getSut()

        var transaction = SentryTransaction(fixture.sentryTracer)
        transaction = processor.process(transaction, null)

        assertEquals("dist", transaction.dist)
    }

    @Test
    fun `User is merged on transaction`() {
        val processor = fixture.getSut(sendDefaultPii = true)

        var transaction = SentryTransaction(fixture.sentryTracer)
        transaction = processor.process(transaction, null)

        assertNotNull(transaction.user)
    }

    private fun generateCrashedEvent(crashedThread: Thread = Thread.currentThread()) = SentryEvent().apply {
        val mockThrowable = mock<Throwable>()
        val actualThrowable = UncaughtExceptionHandlerIntegration.getUnhandledThrowable(crashedThread, mockThrowable)
        throwable = actualThrowable
    }

    internal class CustomCachedApplyScopeDataHint : Cached, ApplyScopeData
}
