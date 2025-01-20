package io.sentry

import io.sentry.hints.AbnormalExit
import io.sentry.hints.ApplyScopeData
import io.sentry.protocol.DebugMeta
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.util.HintUtils
import org.awaitility.kotlin.await
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.RuntimeException
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainEventProcessorTest {
    class Fixture {
        val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            release = "release"
            dist = "dist"
            sdkVersion = SdkVersion("test", "1.2.3")
        }
        val scopes = mock<IScopes>()
        val getLocalhost = mock<InetAddress>()
        lateinit var sentryTracer: SentryTracer
        private val hostnameCacheMock = Mockito.mockStatic(HostnameCache::class.java)

        fun getSut(
            attachThreads: Boolean = true,
            attachStackTrace: Boolean = true,
            environment: String? = "environment",
            tags: Map<String, String> = emptyMap(),
            sendDefaultPii: Boolean? = null,
            serverName: String? = "server",
            host: String? = null,
            resolveHostDelay: Long? = null,
            hostnameCacheDuration: Long = 10,
            proguardUuid: String? = null,
            bundleIds: List<String>? = null,
            modules: Map<String, String>? = null
        ): MainEventProcessor {
            sentryOptions.isAttachThreads = attachThreads
            sentryOptions.isAttachStacktrace = attachStackTrace
            sentryOptions.isAttachServerName = true
            sentryOptions.environment = environment
            sentryOptions.serverName = serverName
            sentryOptions.setModulesLoader { modules }
            if (sendDefaultPii != null) {
                sentryOptions.isSendDefaultPii = sendDefaultPii
            }
            if (proguardUuid != null) {
                sentryOptions.proguardUuid = proguardUuid
            }
            bundleIds?.let { it.forEach { sentryOptions.addBundleId(it) } }
            tags.forEach { sentryOptions.setTag(it.key, it.value) }
            whenever(getLocalhost.canonicalHostName).thenAnswer {
                if (resolveHostDelay != null) {
                    Thread.sleep(resolveHostDelay)
                }
                host
            }
            whenever(scopes.options).thenReturn(sentryOptions)
            sentryTracer = SentryTracer(TransactionContext("", ""), scopes)

            val hostnameCache = HostnameCache(hostnameCacheDuration) { getLocalhost }
            hostnameCacheMock.`when`<Any> { HostnameCache.getInstance() }.thenReturn(hostnameCache)

            return MainEventProcessor(sentryOptions)
        }

        fun teardown() {
            hostnameCacheMock.close()
        }
    }

    @AfterTest
    fun teardown() {
        fixture.teardown()
    }

    private val fixture = Fixture()

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flagged, mechanism added`() {
        val sut = fixture.getSut()

        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, Hint())

        assertNotNull(event.exceptions) {
            assertSame(crashedThread.id, it.first().threadId)
            assertNotNull(it.first().mechanism) { mechanism ->
                assertNotNull(mechanism.isHandled) { isHandled ->
                    assertFalse(isHandled)
                }
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
        event = sut.process(event, Hint())

        assertNotNull(event.threads) { threads ->
            assertTrue(threads.any { it.isCrashed == true })
        }
    }

    @Test
    fun `When hint is not Cached, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, Hint())

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertNotNull(event.threads) {
            assertTrue(it.first { t -> t.id == crashedThread.id }.isCrashed == true)
        }
    }

    @Test
    fun `When hint is ApplyScopeData, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        val hints = HintUtils.createWithTypeCheckHint(mock<ApplyScopeData>())
        event = sut.process(event, hints)

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertNotNull(event.threads) {
            assertTrue(it.first { t -> t.id == crashedThread.id }.isCrashed == true)
        }
    }

    @Test
    fun `data should be applied only if event doesn't have them`() {
        val sut = fixture.getSut()
        var event = generateCrashedEvent()
        event.dist = "eventDist"
        event.environment = "eventEnvironment"
        event.release = "eventRelease"
        event.serverName = "eventServerName"

        event = sut.process(event, Hint())

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

        val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
        event = sut.process(event, hints)

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

        val hints = HintUtils.createWithTypeCheckHint(CustomCachedApplyScopeDataHint())
        event = sut.process(event, hints)

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertNotNull(event.threads) {
            assertTrue(it.first { t -> t.id == crashedThread.id }.isCrashed == true)
        }
    }

    @Test
    fun `when attach threads is disabled, threads should not be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = false)

        var event = SentryEvent()
        event = sut.process(event, Hint())

        assertNull(event.threads)
    }

    @Test
    fun `when attach threads is enabled, threads should be set`() {
        val sut = fixture.getSut()

        var event = SentryEvent()
        event = sut.process(event, Hint())

        assertNotNull(event.threads)
    }

    @Test
    fun `when attach threads is disabled, but attach stacktrace is enabled, current thread should be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = true)

        var event = SentryEvent()
        event = sut.process(event, Hint())

        assertNotNull(event.threads) {
            assertEquals(1, it.count())
        }
    }

    @Test
    fun `when attach threads is disabled, but attach stacktrace is enabled and has exceptions, threads should not be set`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = true)

        var event = SentryEvent(RuntimeException("error"))
        event = sut.process(event, Hint())

        assertNull(event.threads)
    }

    @Test
    fun `when attach threads is disabled, but the hint is Abnormal, still sets threads`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = false)

        var event = SentryEvent(RuntimeException("error"))
        val hint = HintUtils.createWithTypeCheckHint(AbnormalHint())
        event = sut.process(event, hint)

        assertNotNull(event.threads)
        assertEquals(1, event.threads!!.count { it.isCrashed == true })
    }

    @Test
    fun `when the hint is Abnormal with ignoreCurrentThread, does not mark thread as crashed`() {
        val sut = fixture.getSut(attachThreads = false, attachStackTrace = false)

        var event = SentryEvent(RuntimeException("error"))
        val hint = HintUtils.createWithTypeCheckHint(AbnormalHint(ignoreCurrentThread = true))
        event = sut.process(event, hint)

        assertNotNull(event.threads)
        assertEquals(0, event.threads!!.count { it.isCrashed == true })
    }

    @Test
    fun `sets sdkVersion in the event`() {
        val sut = fixture.getSut()
        val event = SentryEvent()
        sut.process(event, Hint())
        assertNotNull(event.sdk) {
            assertEquals(it.name, "test")
            assertEquals(it.version, "1.2.3")
        }
    }

    @Test
    fun `when event and SentryOptions do not have environment set, sets production as environment`() {
        val sut = fixture.getSut(environment = null)
        val event = SentryEvent()
        sut.process(event, Hint())
        assertEquals("production", event.environment)
    }

    @Test
    fun `when event does not have ip address set, sets {{auto}} as the ip address`() {
        val sut = fixture.getSut(sendDefaultPii = true)
        val event = SentryEvent()
        sut.process(event, Hint())
        assertNotNull(event.user) {
            assertEquals("{{auto}}", it.ipAddress)
        }
    }

    @Test
    fun `when event does not have ip address set, do not enrich ip address if sendDefaultPii is false`() {
        val sut = fixture.getSut(sendDefaultPii = false)
        val event = SentryEvent()
        sut.process(event, Hint())
        assertNotNull(event.user) {
            assertNull(it.ipAddress)
        }
    }

    @Test
    fun `when event has ip address set, keeps original ip address`() {
        val sut = fixture.getSut(sendDefaultPii = true)
        val event = SentryEvent()
        event.user = User().apply {
            ipAddress = "192.168.0.1"
        }
        sut.process(event, Hint())
        assertNotNull(event.user) {
            assertEquals("192.168.0.1", it.ipAddress)
        }
    }

    @Test
    fun `when event has environment set, does not overwrite environment`() {
        val sut = fixture.getSut(environment = null)
        val event = SentryEvent()
        event.environment = "staging"
        sut.process(event, Hint())
        assertEquals("staging", event.environment)
    }

    @Test
    fun `when event does not have environment set and SentryOptions have environment set, uses environment from SentryOptions`() {
        val sut = fixture.getSut(environment = "custom")
        val event = SentryEvent()
        sut.process(event, Hint())
        assertEquals("custom", event.environment)
    }

    @Test
    fun `sets tags from SentryOptions`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1", "tag2" to "value2"))
        val event = SentryEvent()
        sut.process(event, Hint())
        assertNotNull(event.tags) {
            assertEquals("value1", it["tag1"])
            assertEquals("value2", it["tag2"])
        }
    }

    @Test
    fun `when event has a tag set with the same name as SentryOptions tags, the tag value from the event is retained`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1", "tag2" to "value2"))
        val event = SentryEvent()
        event.setTag("tag2", "event-tag-value")
        sut.process(event, Hint())
        assertNotNull(event.tags) {
            assertEquals("value1", it["tag1"])
            assertEquals("event-tag-value", it["tag2"])
        }
    }

    @Test
    fun `sets servername retrieved from the local address`() {
        val processor = fixture.getSut(serverName = null, host = "aHost")
        val event = SentryEvent()
        processor.process(event, Hint())
        assertEquals("aHost", event.serverName)
    }

    @Test
    fun `sets servername to null if retrieving takes longer time`() {
        val processor = fixture.getSut(serverName = null, host = "aHost", resolveHostDelay = 2000)
        val event = SentryEvent()
        processor.process(event, Hint())
        assertNull(event.serverName)
    }

    @Test
    fun `uses cache to retrieve servername for subsequent events`() {
        val processor =
            fixture.getSut(serverName = null, host = "aHost", hostnameCacheDuration = 1000)
        val firstEvent = SentryEvent()
        processor.process(firstEvent, Hint())
        assertEquals("aHost", firstEvent.serverName)
        val secondEvent = SentryEvent()
        processor.process(secondEvent, Hint())
        assertEquals("aHost", secondEvent.serverName)
        verify(fixture.getLocalhost, times(1)).canonicalHostName
    }

    @Test
    fun `when cache expires, retrieves new host name from the local address`() {
        val processor = fixture.getSut(serverName = null, host = "aHost")
        val firstEvent = SentryEvent()
        processor.process(firstEvent, Hint())
        assertEquals("aHost", firstEvent.serverName)

        reset(fixture.getLocalhost)
        whenever(fixture.getLocalhost.canonicalHostName).thenReturn("newHost")

        await.untilAsserted {
            val secondEvent = SentryEvent()
            processor.process(secondEvent, Hint())
            assertEquals("newHost", secondEvent.serverName)
        }
    }

    @Test
    fun `does not set serverName on events that already have server names`() {
        val processor = fixture.getSut(serverName = null, host = "aHost")
        val event = SentryEvent()
        event.serverName = "eventHost"
        processor.process(event, Hint())
        assertEquals("eventHost", event.serverName)
    }

    @Test
    fun `does not set serverName on events if serverName is set on SentryOptions`() {
        val processor = fixture.getSut(serverName = "optionsHost", host = "aHost")
        val event = SentryEvent()
        processor.process(event, Hint())
        assertEquals("optionsHost", event.serverName)
    }

    @Test
    fun `Server name is set on transaction`() {
        val processor = fixture.getSut(serverName = "optionsHost")

        var transaction = SentryTransaction(fixture.sentryTracer)
        transaction = processor.process(transaction, Hint())

        assertEquals("optionsHost", transaction.serverName)
    }

    @Test
    fun `Dist is set on transaction`() {
        val processor = fixture.getSut()

        var transaction = SentryTransaction(fixture.sentryTracer)
        transaction = processor.process(transaction, Hint())

        assertEquals("dist", transaction.dist)
    }

    @Test
    fun `User is merged on transaction`() {
        val processor = fixture.getSut(sendDefaultPii = true)

        var transaction = SentryTransaction(fixture.sentryTracer)
        transaction = processor.process(transaction, Hint())

        assertNotNull(transaction.user)
    }

    @Test
    fun `when event has Cached hint, current thread should not be set`() {
        val sut = fixture.getSut(attachThreads = false)

        var event = SentryEvent()

        val hints = HintUtils.createWithTypeCheckHint(CustomCachedApplyScopeDataHint())
        event = sut.process(event, hints)

        assertNull(event.threads)
    }

    @Test
    fun `when event does not have debug meta and proguard uuids are set, attaches debug information`() {
        val sut = fixture.getSut(proguardUuid = "id1")

        var event = SentryEvent()
        event = sut.process(event, Hint())

        assertNotNull(event.debugMeta) {
            assertNotNull(it.images) { images ->
                assertEquals("id1", images[0].uuid)
                assertEquals("proguard", images[0].type)
            }
        }
    }

    @Test
    fun `when event does not have debug meta and bundle ids are set, attaches debug information`() {
        val sut = fixture.getSut(bundleIds = listOf("id1", "id2"))

        var event = SentryEvent()
        event = sut.process(event, Hint())

        assertNotNull(event.debugMeta) {
            assertNotNull(it.images) { images ->
                assertEquals("id1", images[0].debugId)
                assertEquals("jvm", images[0].type)
                assertEquals("id2", images[1].debugId)
                assertEquals("jvm", images[1].type)
            }
        }
    }

    @Test
    fun `when event has debug meta and proguard uuids are set, attaches debug information`() {
        val sut = fixture.getSut(proguardUuid = "id1")

        var event = SentryEvent()
        event.debugMeta = DebugMeta()
        event = sut.process(event, Hint())

        assertNotNull(event.debugMeta) {
            assertNotNull(it.images) { images ->
                assertEquals("id1", images[0].uuid)
                assertEquals("proguard", images[0].type)
            }
        }
    }

    @Test
    fun `when event has debug meta and bundle ids are set, attaches debug information`() {
        val sut = fixture.getSut(bundleIds = listOf("id1", "id2"))

        var event = SentryEvent()
        event.debugMeta = DebugMeta()
        event = sut.process(event, Hint())

        assertNotNull(event.debugMeta) {
            assertNotNull(it.images) { images ->
                assertEquals("id1", images[0].debugId)
                assertEquals("jvm", images[0].type)
                assertEquals("id2", images[1].debugId)
                assertEquals("jvm", images[1].type)
            }
        }
    }

    @Test
    fun `when event has debug meta as well as images and bundle ids are set, attaches debug information`() {
        val sut = fixture.getSut(bundleIds = listOf("id1", "id2"))

        var event = SentryEvent()
        event.debugMeta = DebugMeta().also {
            it.images = listOf()
        }
        event = sut.process(event, Hint())

        assertNotNull(event.debugMeta) {
            assertNotNull(it.images) { images ->
                assertEquals("id1", images[0].debugId)
                assertEquals("jvm", images[0].type)
                assertEquals("id2", images[1].debugId)
                assertEquals("jvm", images[1].type)
            }
        }
    }

    @Test
    fun `when processor is closed, closes hostname cache`() {
        val sut = fixture.getSut(serverName = null)

        sut.process(SentryTransaction(fixture.sentryTracer), Hint())

        sut.close()
        assertNotNull(sut.hostnameCache) {
            assertTrue(it.isClosed)
        }
    }

    @Test
    fun `when event has modules, appends to them`() {
        val sut = fixture.getSut(modules = mapOf("group1:artifact1" to "2.0.0"))

        var event = SentryEvent().apply {
            modules = mapOf("group:artifact" to "1.0.0")
        }
        event = sut.process(event, Hint())

        assertEquals(2, event.modules!!.size)
        assertEquals("1.0.0", event.modules!!["group:artifact"])
        assertEquals("2.0.0", event.modules!!["group1:artifact1"])
    }

    @Test
    fun `sets event modules`() {
        val sut = fixture.getSut(modules = mapOf("group1:artifact1" to "2.0.0"))

        var event = SentryEvent()
        event = sut.process(event, Hint())

        assertEquals(1, event.modules!!.size)
        assertEquals("2.0.0", event.modules!!["group1:artifact1"])
    }

    @Test
    fun `sets debugMeta for transactions`() {
        val sut = fixture.getSut(proguardUuid = "id1")

        var transaction = SentryTransaction(fixture.sentryTracer)
        transaction.debugMeta = DebugMeta()
        transaction = sut.process(transaction, Hint())

        assertNotNull(transaction.debugMeta) {
            assertNotNull(it.images) { images ->
                assertEquals("id1", images[0].uuid)
                assertEquals("proguard", images[0].type)
            }
        }
    }

    @Test
    fun `enriches ReplayEvent`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1"))

        var replayEvent = SentryReplayEvent()
        replayEvent = sut.process(replayEvent, Hint())

        assertEquals("release", replayEvent.release)
        assertEquals("environment", replayEvent.environment)
        assertEquals("dist", replayEvent.dist)
        assertEquals("1.2.3", replayEvent.sdk!!.version)
        assertEquals("test", replayEvent.sdk!!.name)
        assertEquals("java", replayEvent.platform)
        assertEquals("value1", replayEvent.tags!!["tag1"])
    }

    @Test
    fun `uses SdkVersion from replay options for replay events`() {
        val sut = fixture.getSut(tags = mapOf("tag1" to "value1"))

        fixture.sentryOptions.sessionReplay.sdkVersion = SdkVersion("dart", "3.2.1")
        var replayEvent = SentryReplayEvent()
        replayEvent = sut.process(replayEvent, Hint())

        assertEquals("3.2.1", replayEvent.sdk!!.version)
        assertEquals("dart", replayEvent.sdk!!.name)
    }

    private fun generateCrashedEvent(crashedThread: Thread = Thread.currentThread()) =
        SentryEvent().apply {
            val mockThrowable = mock<Throwable>()
            val actualThrowable = UncaughtExceptionHandlerIntegration.getUnhandledThrowable(
                crashedThread,
                mockThrowable
            )
            throwable = actualThrowable
        }

    private class AbnormalHint(private val ignoreCurrentThread: Boolean = false) : AbnormalExit {
        override fun mechanism(): String? = null

        override fun ignoreCurrentThread(): Boolean = ignoreCurrentThread

        override fun timestamp(): Long? = null
    }
}
