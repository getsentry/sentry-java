package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.mockingDetails
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.exception.SentryEnvelopeException
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Cached
import io.sentry.hints.DiskFlushNotification
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Request
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.test.callMethod
import io.sentry.transport.ITransport
import io.sentry.transport.ITransportGate
import org.junit.Assert.assertArrayEquals
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.charset.Charset
import java.util.Arrays
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SentryClientTest {

    private class Fixture {
        var transport = mock<ITransport>()
        var factory = mock<ITransportFactory>()
        val maxAttachmentSize: Long = (5 * 1024 * 1024).toLong()
        val hub = mock<IHub>()
        val sentryTracer = SentryTracer(TransactionContext("a-transaction", "op"), hub)
        val sessionUpdater = mock<SessionUpdater>()

        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            sdkVersion = SdkVersion("test", "1.2.3")
            setDebug(true)
            setDiagnosticLevel(SentryLevel.DEBUG)
            setSerializer(JsonSerializer(this))
            setLogger(mock())
            maxAttachmentSize = this@Fixture.maxAttachmentSize
            setTransportFactory(factory)
            release = "0.0.1"
            isTraceSampling = true
        }

        init {
            whenever(factory.create(any(), any())).thenReturn(transport)
            whenever(hub.options).thenReturn(sentryOptions)
        }

        var attachment = Attachment("hello".toByteArray(), "hello.txt", "text/plain", true)

        fun getSut() = SentryClient(sentryOptions, sessionUpdater)
    }

    private val fixture = Fixture()

    @Test
    fun `when fixture is unchanged, client is enabled`() {
        val sut = fixture.getSut()
        assertTrue(sut.isEnabled)
    }

    @Test
    fun `when dsn is an invalid string, client throws`() {
        fixture.sentryOptions.dsn = "invalid-dsn"
        assertFailsWith<IllegalArgumentException> { fixture.getSut() }
    }

    @Test
    fun `when dsn is null, client throws`() {
        fixture.sentryOptions.dsn = null
        assertFailsWith<IllegalArgumentException> { fixture.getSut() }
    }

    @Test
    fun `when dsn without private key is valid, client is enabled`() {
        fixture.sentryOptions.dsn = dsnString
        val sut = fixture.getSut()
        assertTrue(sut.isEnabled)
    }

    @Test
    fun `when dsn with secret is valid, client is enabled`() {
        fixture.sentryOptions.dsn = dsnStringLegacy
        val sut = fixture.getSut()
        assertTrue(sut.isEnabled)
    }

    @Test
    fun `when client is closed, client gets disabled`() {
        val sut = fixture.getSut()
        assertTrue(sut.isEnabled)
        sut.close()
        assertFalse(sut.isEnabled)
    }

    @Test
    fun `when client is closed, hostname cache is closed`() {
        val sut = fixture.getSut()
        assertTrue(sut.isEnabled)
        sut.close()
        val mainEventProcessor = fixture.sentryOptions.eventProcessors
            .filterIsInstance<MainEventProcessor>()
            .first()
        assertTrue(mainEventProcessor.isClosed)
    }

    @Test
    fun `when beforeSend is set, callback is invoked`() {
        var invoked = false
        fixture.sentryOptions.setBeforeSend { e, _ -> invoked = true; e }
        val sut = fixture.getSut()
        sut.captureEvent(SentryEvent())
        assertTrue(invoked)
    }

    @Test
    fun `when beforeSend is returns null, event is dropped`() {
        fixture.sentryOptions.setBeforeSend { _: SentryEvent, _: Any? -> null }
        val sut = fixture.getSut()
        val event = SentryEvent()
        sut.captureEvent(event)
        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when beforeSend is returns new instance, new instance is sent`() {
        val expected = SentryEvent().apply {
            setTag("test", "test")
        }
        fixture.sentryOptions.setBeforeSend { _, _ -> expected }
        val sut = fixture.getSut()
        val actual = SentryEvent()
        sut.captureEvent(actual)
        verify(fixture.transport).send(
            check {
                val event = getEventFromData(it.items.first().data)
                assertEquals("test", event.tags!!["test"])
            },
            anyOrNull()
        )
        verifyNoMoreInteractions(fixture.transport)
    }

    @Test
    fun `when beforeSend throws an exception, breadcrumb is added and event is sent`() {
        val exception = Exception("test")

        exception.stackTrace.toString()
        fixture.sentryOptions.setBeforeSend { _, _ -> throw exception }
        val sut = fixture.getSut()
        val actual = SentryEvent()
        sut.captureEvent(actual)

        assertNotNull(actual.breadcrumbs) {
            assertEquals("test", it.first().data["sentry:message"])
            assertEquals("SentryClient", it.first().category)
            assertEquals(SentryLevel.ERROR, it.first().level)
            assertEquals("BeforeSend callback failed.", it.first().message)
        }
    }

    @Test
    fun `when event captured with hint, hint passed to connection`() {
        val event = SentryEvent()
        fixture.sentryOptions.environment = "not to be applied"
        val sut = fixture.getSut()
        val expectedHint = Object()
        sut.captureEvent(event, expectedHint)
        verify(fixture.transport).send(any(), eq(expectedHint))
    }

    @Test
    fun `when captureMessage is called, sentry event contains formatted message`() {
        var sentEvent: SentryEvent? = null
        fixture.sentryOptions.setBeforeSend { e, _ -> sentEvent = e; e }
        val sut = fixture.getSut()
        val actual = "actual message"
        sut.callMethod(
            "captureMessage",
            parameterTypes = arrayOf(
                String::class.java,
                SentryLevel::class.java,
                Scope::class.java
            ),
            actual,
            null,
            null
        )
        assertEquals(actual, sentEvent!!.message!!.formatted)
    }

    @Test
    fun `when captureMessage is called, sentry event contains level`() {
        var sentEvent: SentryEvent? = null
        fixture.sentryOptions.setBeforeSend { e, _ -> sentEvent = e; e }
        val sut = fixture.getSut()
        sut.callMethod(
            "captureMessage",
            parameterTypes = arrayOf(String::class.java, SentryLevel::class.java),
            null,
            SentryLevel.DEBUG
        )
        assertEquals(SentryLevel.DEBUG, sentEvent!!.level)
    }

    @Test
    fun `when event has release, value from options not applied`() {
        val event = SentryEvent()
        val expected = "original"
        fixture.sentryOptions.environment = "not to be applied"
        event.release = expected
        val sut = fixture.getSut()
        sut.captureEvent(event)
        assertEquals(expected, event.release)
    }

    @Test
    fun `when event doesn't have release, value from options applied`() {
        val event = SentryEvent()
        val expected = "original"
        fixture.sentryOptions.release = expected
        val sut = fixture.getSut()
        sut.captureEvent(event)
        assertEquals(expected, event.release)
    }

    @Test
    fun `when event has environment, value from options not applied`() {
        val event = SentryEvent()
        val expected = "original"
        fixture.sentryOptions.environment = "not to be applied"
        event.environment = expected
        val sut = fixture.getSut()
        sut.captureEvent(event)
        assertEquals(expected, event.environment)
    }

    @Test
    fun `when event doesn't have environment, value from options applied`() {
        val event = SentryEvent()
        val expected = "original"
        fixture.sentryOptions.environment = expected
        val sut = fixture.getSut()
        sut.captureEvent(event)
        assertEquals(expected, event.environment)
    }

    @Test
    fun `when captureEvent with scope, event should have its data if not set`() {
        val event = SentryEvent()
        val scope = createScope()

        val sut = fixture.getSut()

        sut.captureEvent(event, scope)
        assertEquals("message", event.breadcrumbs!![0].message)
        assertNotNull(event.extras) {
            assertEquals("extra", it["extra"])
        }
        assertEquals("tags", event.tags!!["tags"])
        assertNotNull(event.fingerprints) {
            assertEquals("fp", it[0])
        }
        assertNotNull(event.user) {
            assertEquals("id", it.id)
        }
        assertEquals(SentryLevel.FATAL, event.level)
        assertNotNull(event.request) {
            assertEquals("post", it.method)
        }
    }

    @Test
    fun `when breadcrumbs are not empty, sort them out by date`() {
        val b1 = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.001Z"))
        val b2 = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.002Z"))
        val scope = Scope(SentryOptions()).apply {
            addBreadcrumb(b2)
            addBreadcrumb(b1)
        }

        val sut = fixture.getSut()

        val b3 = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.003Z"))
        val event = SentryEvent().apply {
            breadcrumbs = mutableListOf(b3)
        }

        sut.captureEvent(event, scope)

        assertNotNull(event.breadcrumbs) {
            assertSame(b1, it[0])
            assertSame(b2, it[1])
            assertSame(b3, it[2])
        }
    }

    @Test
    fun `when captureEvent with scope, event data has priority over scope but level and it should append extras, tags and breadcrumbs`() {
        val event = createEvent()

        val scope = createScope()

        val sut = fixture.getSut()

        sut.captureEvent(event, scope)

        // breadcrumbs are appending
        assertNotNull(event.breadcrumbs) {
            assertEquals("eventMessage", it[0].message)
            assertEquals("message", it[1].message)
        }

        // extras are appending
        assertNotNull(event.extras) {
            assertEquals("eventExtra", it["eventExtra"])
            assertEquals("extra", it["extra"])
        }

        // tags are appending
        assertNotNull(event.tags) {
            assertEquals("eventTag", it["eventTag"])
            assertEquals("tags", it["tags"])
        }

        // fingerprint is replaced
        assertNotNull(event.fingerprints) {
            assertEquals("eventFp", it[0])
            assertEquals(1, it.size)
        }

        assertEquals("eventTransaction", event.transaction)

        assertNotNull(event.user) {
            assertEquals("eventId", it.id)
        }

        assertEquals(SentryLevel.FATAL, event.level)
    }

    @Test
    fun `when captureEvent with scope, event extras and tags are only append if key is absent`() {
        val event = createEvent()

        val scope = createScope()
        scope.setExtra("eventExtra", "extra")
        scope.setTag("eventTag", "tags")

        val sut = fixture.getSut()

        sut.captureEvent(event, scope)

        // extras are appending
        assertNotNull(event.extras) {
            assertEquals("eventExtra", it["eventExtra"])
        }

        // tags are appending
        assertNotNull(event.tags) {
            assertEquals("eventTag", it["eventTag"])
        }
    }

    @Test
    fun `when captureEvent with scope, event should have its level if set`() {
        val event = SentryEvent()
        event.level = SentryLevel.DEBUG
        val scope = createScope()

        val sut = fixture.getSut()

        sut.captureEvent(event, scope)
        assertEquals(SentryLevel.FATAL, event.level)
    }

    @Test
    fun `when captureEvent with sampling, some events not captured`() {
        fixture.sentryOptions.sampleRate = 0.000000001
        val sut = fixture.getSut()

        val allEvents = 10
        (0..allEvents).forEach { _ -> sut.captureEvent(SentryEvent()) }
        assertTrue(allEvents > mockingDetails(fixture.transport).invocations.size)
    }

    @Test
    fun `when captureEvent without sampling, all events are captured`() {
        fixture.sentryOptions.sampleRate = null
        val sut = fixture.getSut()

        val allEvents = 10
        (0..allEvents).forEach { _ -> sut.captureEvent(SentryEvent()) }
        assertEquals(
            allEvents,
            mockingDetails(fixture.transport).invocations.size - 1
        ) // 1 extra invocation outside .send()
    }

    @Test
    fun `when captureEvent with attachments`() {
        val event = createEvent()

        fixture.getSut().captureEvent(event, createScopeWithAttachments())

        verifyAttachmentsInEnvelope(event.eventId)
    }

    @Test
    fun `when captureUserFeedback with empty id, envelope is not sent`() {
        val sut = fixture.getSut()

        sut.captureUserFeedback(UserFeedback(SentryId.EMPTY_ID))

        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when captureUserFeedback, envelope is sent`() {
        val sut = fixture.getSut()

        sut.captureUserFeedback(userFeedback)

        verify(fixture.transport).send(
            check { actual ->
                assertEquals(userFeedback.eventId, actual.header.eventId)
                assertEquals(fixture.sentryOptions.sdkVersion, actual.header.sdkVersion)

                assertEquals(1, actual.items.count())
                val item = actual.items.first()
                assertEquals(SentryItemType.UserFeedback, item.header.type)
                assertEquals("application/json", item.header.contentType)

                assertEnvelopeItemDataForUserFeedback(item)
            }
        )
    }

    private fun assertEnvelopeItemDataForUserFeedback(item: SentryEnvelopeItem) {
        val stream = ByteArrayOutputStream()
        val writer = stream.bufferedWriter(Charset.forName("UTF-8"))
        fixture.sentryOptions.serializer.serialize(userFeedback, writer)
        val expectedData = stream.toByteArray()
        assertTrue(Arrays.equals(expectedData, item.data))
    }

    @Test
    fun `when captureUserFeedback and connection throws, log exception`() {
        val sut = fixture.getSut()

        val exception = IOException("No connection")
        whenever(fixture.transport.send(any())).thenThrow(exception)

        val logger = mock<ILogger>()
        fixture.sentryOptions.setLogger(logger)

        sut.captureUserFeedback(userFeedback)

        verify(logger)
            .log(
                SentryLevel.WARNING, exception,
                "Capturing user feedback %s failed.", userFeedback.eventId
            )
    }

    @Test
    fun `when hint is Cached, scope is not applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL
        sut.captureEvent(event, scope, mock<Cached>())

        assertNotEquals(scope.level, event.level)
    }

    @Test
    fun `when hint is not Cached, scope is applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL
        sut.captureEvent(event, scope, Object())

        assertEquals(scope.level, event.level)
    }

    @Test
    fun `when hint is ApplyScopeData, scope is applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL
        sut.captureEvent(event, scope, mock<ApplyScopeData>())

        assertEquals(scope.level, event.level)
    }

    @Test
    fun `when hint is Cached but also ApplyScopeData, scope is applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL
        sut.captureEvent(event, scope, mock<CustomCachedApplyScopeDataHint>())

        assertEquals(scope.level, event.level)
    }

    @Test
    fun `when transport factory is NoOp, it should initialize it`() {
        fixture.sentryOptions.setTransportFactory(NoOpTransportFactory.getInstance())
        fixture.getSut()
        assertTrue(fixture.sentryOptions.transportFactory is AsyncHttpTransportFactory)
    }

    @Test
    fun `when transport factory is set on options, it should use the custom transport factory`() {
        val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
        }
        val transportFactory = mock<ITransportFactory>()
        sentryOptions.setTransportFactory(transportFactory)

        SentryClient(sentryOptions, mock())

        assertEquals(transportFactory, sentryOptions.transportFactory)
    }

    @Test
    fun `when transport gate is set on options, it should use the custom transport gate`() {
        val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
        }
        val transportGate = CustomTransportGate()
        sentryOptions.setTransportGate(transportGate)

        SentryClient(sentryOptions, mock())

        assertEquals(transportGate, sentryOptions.transportGate)
    }

    @Test
    fun `when transport gate is null, it should init an always on transport gate`() {
        val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
        }

        SentryClient(sentryOptions, mock())

        assertNotNull(sentryOptions.transportGate)
        assertTrue(sentryOptions.transportGate.isConnected)
    }

    @Test
    fun `when scope has event processors, they should be applied`() {
        val event = SentryEvent()
        val scope = createScope()
        val processor = mock<EventProcessor>()
        whenever(processor.process(any<SentryEvent>(), anyOrNull())).thenReturn(event)
        scope.addEventProcessor(processor)

        val sut = fixture.getSut()

        sut.captureEvent(event, scope)
        verify(processor).process(eq(event), anyOrNull())
    }

    @Test
    fun `when scope has event processors, apply for transactions`() {
        val transaction = SentryTransaction(fixture.sentryTracer)
        val scope = createScope()
        val processor = mock<EventProcessor>()
        whenever(processor.process(any<SentryTransaction>(), anyOrNull())).thenReturn(transaction)
        scope.addEventProcessor(processor)

        val sut = fixture.getSut()

        sut.captureTransaction(transaction, scope, null)
        verify(processor).process(eq(transaction), anyOrNull())
    }

    @Test
    fun `when options have event processors, they should be applied`() {
        val processor = mock<EventProcessor>()
        fixture.sentryOptions.addEventProcessor(processor)

        val event = SentryEvent()

        fixture.getSut().captureEvent(event)
        verify(processor).process(eq(event), anyOrNull())
    }

    @Test
    fun `when options have event processors, apply for transactions`() {
        val processor = mock<EventProcessor>()
        fixture.sentryOptions.addEventProcessor(processor)

        val transaction = SentryTransaction(fixture.sentryTracer)

        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceState())
        verify(processor).process(eq(transaction), anyOrNull())
    }

    @Test
    fun `when captureSession and no release is set, do nothing`() {
        fixture.getSut().captureSession(createSession(""))
        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when captureSession and release is set, send an envelope`() {
        fixture.getSut().captureSession(createSession())
        verify(fixture.transport).send(any(), anyOrNull())
    }

    @Test
    fun `when captureSession, sdkInfo should be in the envelope header`() {
        fixture.getSut().captureSession(createSession())
        verify(fixture.transport).send(
            check {
                assertNotNull(it.header.sdkVersion)
            },
            anyOrNull()
        )
    }

    @Test
    fun `when captureEnvelope and thres an exception, returns empty sentryId`() {
        whenever(fixture.transport.send(any(), anyOrNull())).thenThrow(IOException())

        val envelope = SentryEnvelope(SentryId(UUID.randomUUID()), null, setOf())
        val sentryId = fixture.getSut().captureEnvelope(envelope)
        assertEquals(SentryId.EMPTY_ID, sentryId)
    }

    @Test
    fun `when captureEnvelope and theres no exception, returns envelope header id`() {
        val expectedSentryId = SentryId(UUID.randomUUID())
        val envelope = SentryEnvelope(expectedSentryId, null, setOf())
        val sentryId = fixture.getSut().captureEnvelope(envelope)
        assertEquals(expectedSentryId, sentryId)
    }

    @Test
    fun `when captureEvent with sampling, session is still updated`() {
        fixture.sentryOptions.sampleRate = 1.0
        val sut = fixture.getSut()

        val event = SentryEvent().apply {
            exceptions = createNonHandledException()
        }
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            sut.captureEvent(event, scope, null)
            verify(fixture.sessionUpdater).updateSessionData(event, null, scope)
        }
    }

    @Test
    fun `when context property is missing on the event, property from scope contexts is applied`() {
        val sut = fixture.getSut()

        val scope = Scope(fixture.sentryOptions)
        scope.setContexts("key", "abc")
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            sut.captureEvent(SentryEvent(), scope, null)
            verify(fixture.transport).send(
                check {
                    val event = getEventFromData(it.items.first().data)
                    val map = event.contexts["key"] as Map<*, *>
                    assertEquals("abc", map["value"])
                },
                anyOrNull()
            )
        }
    }

    @Test
    fun `when contexts property is set on the event, property from scope contexts is not applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        event.contexts["key"] = "event value"
        val scope = Scope(fixture.sentryOptions)
        scope.setContexts("key", "scope value")
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            sut.captureEvent(event, scope, null)
            verify(fixture.transport).send(
                check {
                    val eventFromData = getEventFromData(it.items.first().data)
                    assertEquals("event value", eventFromData.contexts["key"])
                },
                anyOrNull()
            )
        }
    }

    @Test
    fun `when contexts are not objects, wrap it up within a value object`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(fixture.sentryOptions)
        scope.setContexts("boolean", true)
        scope.setContexts("string", "test")
        scope.setContexts("number", 1)
        scope.setContexts("collection", listOf("a", "b"))
        scope.setContexts("array", arrayOf("a", "b"))
        scope.setContexts("char", 'a')

        sut.captureEvent(event, scope, null)
        verify(fixture.transport).send(
            check {
                val contexts = getEventFromData(it.items.first().data).contexts

                val bolKey = contexts["boolean"] as Map<*, *>
                assertTrue(bolKey["value"] as Boolean)

                val strKey = contexts["string"] as Map<*, *>
                assertEquals("test", strKey["value"])

                val numKey = contexts["number"] as Map<*, *>
                assertEquals(1, numKey["value"])

                val listKey = contexts["collection"] as Map<*, *>
                assertEquals("a", (listKey["value"] as List<*>)[0])
                assertEquals("b", (listKey["value"] as List<*>)[1])

                val arrKey = contexts["array"] as Map<*, *>
                assertEquals("a", (arrKey["value"] as List<*>)[0])
                assertEquals("b", (arrKey["value"] as List<*>)[1])

                val charKey = contexts["char"] as Map<*, *>
                assertEquals("a", charKey["value"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `exception thrown by an event processor is handled gracefully`() {
        fixture.sentryOptions.addEventProcessor(eventProcessorThrows())
        val sut = fixture.getSut()
        sut.captureEvent(SentryEvent())
    }

    @Test
    fun `transactions are sent using connection`() {
        val sut = fixture.getSut()
        sut.captureTransaction(
            SentryTransaction(fixture.sentryTracer),
            Scope(fixture.sentryOptions),
            null
        )
        verify(fixture.transport).send(
            check {
                val transaction = it.items.first().getTransaction(fixture.sentryOptions.serializer)
                assertNotNull(transaction)
                assertEquals("a-transaction", transaction.transaction)
            },
            eq(null)
        )
    }

    @Test
    fun `when captureTransaction with attachments`() {
        val transaction = SentryTransaction(fixture.sentryTracer)
        fixture.getSut().captureTransaction(transaction, createScopeWithAttachments(), null)

        verifyAttachmentsInEnvelope(transaction.eventId)
    }

    @Test
    fun `when captureTransaction with attachments not added to transaction`() {
        val transaction = SentryTransaction(fixture.sentryTracer)
        val scope = createScopeWithAttachments()
        scope.addAttachment(Attachment("hello".toByteArray(), "application/octet-stream"))
        fixture.getSut().captureTransaction(transaction, scope, null)

        verifyAttachmentsInEnvelope(transaction.eventId)
    }

    @Test
    fun `when captureTransaction scope is applied to transaction`() {
        val sut = fixture.getSut()
        val scope = Scope(fixture.sentryOptions)
        scope.setTag("tag1", "value1")
        scope.setContexts("context-key", "context-value")
        scope.request = Request().apply {
            url = "/url"
        }
        scope.addBreadcrumb(Breadcrumb("message"))
        scope.setExtra("a", "b")

        sut.captureTransaction(SentryTransaction(fixture.sentryTracer), scope, null)
        verify(fixture.transport).send(
            check { envelope ->
                val transaction =
                    envelope.items.first().getTransaction(fixture.sentryOptions.serializer)
                assertNotNull(transaction) {
                    assertEquals("value1", it.getTag("tag1"))
                    assertEquals(mapOf("value" to "context-value"), it.contexts["context-key"])
                    assertNotNull(it.request) { request ->
                        assertEquals("/url", request.url)
                    }
                    assertNotNull(it.breadcrumbs) { breadcrumbs ->
                        assertEquals("message", breadcrumbs.first().message)
                    }
                    assertEquals("b", it.getExtra("a"))
                }
            },
            eq(null)
        )
    }

    @Test
    fun `when captureTransaction with scope, transaction should use user data`() {
        val transaction = SentryTransaction(SentryTracer(TransactionContext("tx", "op"), mock()))
        val scope = createScope()

        val sut = fixture.getSut()

        sut.captureTransaction(transaction, scope, null)
        assertNotNull(transaction.user) {
            assertEquals("id", it.id)
        }
    }

    @Test
    fun `when scope's active span is a transaction, transaction context is applied to an event`() {
        val event = SentryEvent()
        val sut = fixture.getSut()
        val scope = createScope()
        val transaction = fixture.sentryTracer
        scope.setTransaction(transaction)
        transaction.finish()
        sut.captureEvent(event, scope)
        assertNotNull(event.contexts.trace)
        assertEquals(transaction.root.spanContext, event.contexts.trace)
    }

    @Test
    fun `when scope's active span is a span, span is applied to an event`() {
        val event = SentryEvent()
        val sut = fixture.getSut()
        val scope = createScope()
        val transaction = SentryTracer(TransactionContext("a-transaction", "op"), fixture.hub)
        scope.setTransaction(transaction)
        val span = transaction.startChild("op")
        sut.captureEvent(event, scope)
        assertNotNull(event.contexts.trace)
        assertEquals(span.spanContext, event.contexts.trace)
    }

    @Test
    fun `when scope has an active transaction, trace state is set on the envelope`() {
        val event = SentryEvent()
        val sut = fixture.getSut()
        val scope = createScope()
        val transaction = fixture.sentryTracer
        scope.setTransaction(transaction)
        transaction.finish()
        sut.captureEvent(event, scope)
        verify(fixture.transport).send(
            check {
                assertNotNull(it.header.trace) {
                    assertEquals(transaction.spanContext.traceId, it.traceId)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `when scope does not have an active transaction, trace state is not set on the envelope`() {
        val sut = fixture.getSut()
        sut.captureEvent(SentryEvent(), createScope())
        verify(fixture.transport).send(
            check {
                assertNull(it.header.trace)
            },
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is captured, trace state is set on the envelope`() {
        val sut = fixture.getSut()
        val transaction = SentryTransaction(fixture.sentryTracer)
        val traceState = fixture.sentryTracer.traceState()
        sut.captureTransaction(transaction, traceState)
        verify(fixture.transport).send(
            check {
                assertEquals(traceState, it.header.trace)
            },
            anyOrNull()
        )
    }

    @Test
    fun `when transaction does not have environment and release set, and the environment is set on options, options values are applied to transactions`() {
        fixture.sentryOptions.release = "optionsRelease"
        fixture.sentryOptions.environment = "optionsEnvironment"
        val sut = fixture.getSut()
        val transaction = SentryTransaction(fixture.sentryTracer)
        sut.captureTransaction(transaction, fixture.sentryTracer.traceState())
        assertEquals("optionsRelease", transaction.release)
        assertEquals("optionsEnvironment", transaction.environment)
    }

    @Test
    fun `when transaction has environment and release set, and the environment is set on options, options values are not applied to transactions`() {
        fixture.sentryOptions.release = "optionsRelease"
        fixture.sentryOptions.environment = "optionsEnvironment"
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.hub)
        val transaction = SentryTransaction(sentryTracer)
        transaction.release = "transactionRelease"
        transaction.environment = "transactionEnvironment"
        sut.captureTransaction(transaction, sentryTracer.traceState())
        assertEquals("transactionRelease", transaction.release)
        assertEquals("transactionEnvironment", transaction.environment)
    }

    @Test
    fun `when transaction does not have SDK version set, and the SDK version is set on options, options values are applied to transactions`() {
        fixture.sentryOptions.sdkVersion = SdkVersion("sdk.name", "version")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.hub)
        val transaction = SentryTransaction(sentryTracer)
        sut.captureTransaction(transaction, sentryTracer.traceState())
        assertEquals(fixture.sentryOptions.sdkVersion, transaction.sdk)
    }

    @Test
    fun `when transaction has SDK version set, and the SDK version is set on options, options values are not applied to transactions`() {
        fixture.sentryOptions.sdkVersion = SdkVersion("sdk.name", "version")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.hub)
        val transaction = SentryTransaction(sentryTracer)
        val sdkVersion = SdkVersion("transaction.sdk.name", "version")
        transaction.sdk = sdkVersion
        sut.captureTransaction(transaction, sentryTracer.traceState())
        assertEquals(sdkVersion, transaction.sdk)
    }

    @Test
    fun `when transaction does not have tags, and tags are set on options, options values are applied to transactions`() {
        fixture.sentryOptions.setTag("tag1", "value1")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.hub)
        val transaction = SentryTransaction(sentryTracer)
        sut.captureTransaction(transaction, sentryTracer.traceState())
        assertEquals(mapOf("tag1" to "value1"), transaction.tags)
    }

    @Test
    fun `when transaction has tags, and tags are set on options, options tags are added to transactions`() {
        fixture.sentryOptions.setTag("tag1", "value1")
        fixture.sentryOptions.setTag("tag2", "value2")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.hub)
        val transaction = SentryTransaction(sentryTracer)
        transaction.setTag("tag3", "value3")
        transaction.setTag("tag2", "transaction-tag")
        sut.captureTransaction(transaction, sentryTracer.traceState())
        assertEquals(
            mapOf("tag1" to "value1", "tag2" to "transaction-tag", "tag3" to "value3"),
            transaction.tags
        )
    }

    @Test
    fun `captured transactions without a platform, have the default platform set`() {
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.hub)
        val transaction = SentryTransaction(sentryTracer)
        sut.captureTransaction(transaction, sentryTracer.traceState())
        assertEquals("java", transaction.platform)
    }

    @Test
    fun `captured transactions with a platform, do not get the platform overwritten`() {
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.hub)
        val transaction = SentryTransaction(sentryTracer)
        transaction.platform = "abc"
        sut.captureTransaction(transaction, sentryTracer.traceState())
        assertEquals("abc", transaction.platform)
    }

    @Test
    fun `when exception type is ignored, capturing event does not send it`() {
        fixture.sentryOptions.addIgnoredExceptionForType(IllegalStateException::class.java)
        val sut = fixture.getSut()
        sut.captureException(IllegalStateException())
        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when captureSessions, sdkInfo should be in the envelope header`() {
        fixture.getSut().captureSessions(mock())
        verify(fixture.transport).send(
            check {
                assertNotNull(it.header.sdkVersion)
            }
        )
    }

    private fun createScope(): Scope {
        return Scope(SentryOptions()).apply {
            addBreadcrumb(
                Breadcrumb().apply {
                    message = "message"
                }
            )
            setExtra("extra", "extra")
            setTag("tags", "tags")
            fingerprint.add("fp")
            level = SentryLevel.FATAL
            user = User().apply {
                id = "id"
            }
            request = Request().apply {
                method = "post"
            }
        }
    }

    private fun createScopeWithAttachments(): Scope {
        return createScope().apply {
            addAttachment(fixture.attachment)
            addAttachment(fixture.attachment)

            val bytesTooBig = ByteArray((fixture.maxAttachmentSize + 1).toInt()) { 0 }
            addAttachment(
                Attachment(
                    bytesTooBig,
                    "will_get_dropped.txt",
                    "application/octet-stream",
                    true
                )
            )
        }
    }

    private fun createEvent(): SentryEvent {
        return SentryEvent().apply {
            addBreadcrumb(
                Breadcrumb().apply {
                    message = "eventMessage"
                }
            )
            setExtra("eventExtra", "eventExtra")
            setTag("eventTag", "eventTag")
            fingerprints = listOf("eventFp")
            transaction = "eventTransaction"
            level = SentryLevel.DEBUG
            user = User().apply {
                id = "eventId"
            }
        }
    }

    private fun createSession(release: String = "rel"): Session {
        return Session("dis", User(), "env", release)
    }

    private val userFeedback: UserFeedback
        get() {
            val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
            val userFeedback = UserFeedback(eventId)
            userFeedback.apply {
                name = "John"
                email = "john@me.com"
                comments = "comment"
            }

            return userFeedback
        }

    internal class CustomTransportGate : ITransportGate {
        override fun isConnected(): Boolean = false
    }

    private fun createNonHandledException(): List<SentryException> {
        val exception = SentryException().apply {
            mechanism = Mechanism().apply {
                isHandled = false
            }
        }
        return listOf(exception)
    }

    private fun getEventFromData(data: ByteArray): SentryEvent {
        val inputStream = InputStreamReader(ByteArrayInputStream(data))
        return fixture.sentryOptions.serializer.deserialize(inputStream, SentryEvent::class.java)!!
    }

    private fun getTransactionFromData(data: ByteArray): SentryTransaction {
        val inputStream = InputStreamReader(ByteArrayInputStream(data))
        return fixture.sentryOptions.serializer.deserialize(
            inputStream,
            SentryTransaction::class.java
        )!!
    }

    private fun verifyAttachmentsInEnvelope(eventId: SentryId?) {
        verify(fixture.transport).send(
            check { actual ->
                assertEquals(eventId, actual.header.eventId)

                assertEquals(fixture.sentryOptions.sdkVersion, actual.header.sdkVersion)

                assertEquals(4, actual.items.count())
                val attachmentItems = actual.items
                    .filter { item -> item.header.type == SentryItemType.Attachment }
                    .toList()

                assertEquals(3, attachmentItems.size)

                val attachmentItem = attachmentItems.first()
                assertEquals(fixture.attachment.contentType, attachmentItem.header.contentType)
                assertEquals(fixture.attachment.filename, attachmentItem.header.fileName)
                assertEquals(fixture.attachment.bytes?.size, attachmentItem.header.length)

                val expectedBytes = fixture.attachment.bytes!!
                assertArrayEquals(expectedBytes, attachmentItem.data)

                val attachmentItemTooBig = attachmentItems.last()
                assertFailsWith<SentryEnvelopeException>(
                    "Getting data from attachment should" +
                        "throw an exception, because the attachment is too big."
                ) {
                    attachmentItemTooBig.data
                }
            },
            isNull()
        )
    }

    internal class CustomCachedApplyScopeDataHint : Cached, ApplyScopeData

    internal class DiskFlushNotificationHint : DiskFlushNotification {
        override fun markFlushed() {}
    }

    private fun eventProcessorThrows(): EventProcessor {
        return object : EventProcessor {
            override fun process(event: SentryEvent, hint: Any?): SentryEvent? {
                throw Throwable()
            }
        }
    }
}
