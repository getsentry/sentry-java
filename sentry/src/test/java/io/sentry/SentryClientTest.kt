package io.sentry

import io.sentry.Scope.IWithPropagationContext
import io.sentry.SentryLevel.WARNING
import io.sentry.Session.State.Crashed
import io.sentry.clientreport.ClientReportTestHelper.Companion.assertClientReport
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import io.sentry.exception.SentryEnvelopeException
import io.sentry.hints.AbnormalExit
import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Backfillable
import io.sentry.hints.Cached
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.TransactionEnd
import io.sentry.protocol.Contexts
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.protocol.ViewHierarchy
import io.sentry.test.callMethod
import io.sentry.transport.ITransport
import io.sentry.transport.ITransportGate
import io.sentry.util.HintUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.msgpack.core.MessagePack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Arrays
import java.util.LinkedList
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SentryClientTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {
        var transport = mock<ITransport>()
        var factory = mock<ITransportFactory>()
        val maxAttachmentSize: Long = (5 * 1024 * 1024).toLong()
        val scopes = mock<IScopes>()
        val sentryTracer: SentryTracer
        val profileChunk: ProfileChunk
        val profilingTraceFile = Files.createTempFile("trace", ".trace").toFile()

        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            sdkVersion = SdkVersion("test", "1.2.3")
            isDebug = true
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
            whenever(scopes.options).thenReturn(sentryOptions)
            sentryTracer = SentryTracer(TransactionContext("a-transaction", "op", TracesSamplingDecision(true)), scopes)
            sentryTracer.startChild("a-span", "span 1").finish()
            profileChunk = ProfileChunk(SentryId(), SentryId(), profilingTraceFile, emptyMap(), 1.0, sentryOptions)
        }

        var attachment = Attachment("hello".toByteArray(), "hello.txt", "text/plain", true)
        var attachment2 = Attachment("hello2".toByteArray(), "hello2.txt", "text/plain", true)
        var attachment3 = Attachment("hello3".toByteArray(), "hello3.txt", "text/plain", true)
        var profilingTraceData = ProfilingTraceData(profilingTraceFile, sentryTracer)
        var profilingNonExistingTraceData = ProfilingTraceData(File("non_existent.trace"), sentryTracer)

        fun getSut(optionsCallback: ((SentryOptions) -> Unit)? = null): SentryClient {
            optionsCallback?.invoke(sentryOptions)
            profilingTraceFile.writeText("sampledProfile")
            return SentryClient(sentryOptions)
        }
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
    fun `when client is closed with isRestarting false, transport waits`() {
        val sut = fixture.getSut()
        assertTrue(sut.isEnabled)
        sut.close(false)
        assertNotEquals(0, fixture.sentryOptions.shutdownTimeoutMillis)
        verify(fixture.transport).flush(eq(fixture.sentryOptions.shutdownTimeoutMillis))
        verify(fixture.transport).close(eq(false))
    }

    @Test
    fun `when client is closed with isRestarting true, transport does not wait`() {
        val sut = fixture.getSut()
        assertTrue(sut.isEnabled)
        sut.close(true)
        verify(fixture.transport).flush(eq(0))
        verify(fixture.transport).close(eq(true))
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

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 1))
        )
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
    fun `when beforeSend throws an exception, event is dropped`() {
        val exception = Exception("test")

        exception.stackTrace.toString()
        fixture.sentryOptions.setBeforeSend { _, _ -> throw exception }
        val sut = fixture.getSut()
        val actual = SentryEvent()
        val id = sut.captureEvent(actual)

        assertEquals(SentryId.EMPTY_ID, id)

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `when event captured with hint, hint passed to connection`() {
        val event = SentryEvent()
        fixture.sentryOptions.environment = "not to be applied"
        val sut = fixture.getSut()

        val hints = HintUtils.createWithTypeCheckHint(Object())
        sut.captureEvent(event, hints)
        verify(fixture.transport).send(any(), eq(hints))
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
                IScope::class.java
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
    fun `events dropped by sampling are recorded as lost`() {
        fixture.sentryOptions.sampleRate = 0.000000001
        val sut = fixture.getSut()

        sut.captureEvent(SentryEvent())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 1))
        )
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
                SentryLevel.WARNING,
                exception,
                "Capturing user feedback %s failed.",
                userFeedback.eventId
            )
    }

    @Test
    fun `when captureCheckIn, envelope is sent`() {
        val sut = fixture.getSut()

        sut.captureCheckIn(checkIn, null, null)

        verify(fixture.transport).send(
            check { actual ->
                assertEquals(checkIn.checkInId, actual.header.eventId)
                assertEquals(fixture.sentryOptions.sdkVersion, actual.header.sdkVersion)

                assertEquals(1, actual.items.count())
                val item = actual.items.first()
                assertEquals(SentryItemType.CheckIn, item.header.type)
                assertEquals("application/json", item.header.contentType)

                assertEnvelopeItemDataForCheckIn(item)
            },
            any<Hint>()
        )
    }

    @Test
    fun `when captureCheckIn, envelope is sent if ignored slug does not match`() {
        val sut = fixture.getSut { options ->
            options.setIgnoredCheckIns(listOf("non_matching_slug"))
        }

        sut.captureCheckIn(checkIn, null, null)

        verify(fixture.transport).send(
            check { actual ->
                assertEquals(checkIn.checkInId, actual.header.eventId)
                assertEquals(fixture.sentryOptions.sdkVersion, actual.header.sdkVersion)

                assertEquals(1, actual.items.count())
                val item = actual.items.first()
                assertEquals(SentryItemType.CheckIn, item.header.type)
                assertEquals("application/json", item.header.contentType)

                assertEnvelopeItemDataForCheckIn(item)
            },
            any<Hint>()
        )
    }

    @Test
    fun `when captureCheckIn, envelope is not sent if slug is ignored`() {
        val sut = fixture.getSut { options ->
            options.setIgnoredCheckIns(listOf("some_slug"))
        }

        sut.captureCheckIn(checkIn, null, null)

        verify(fixture.transport, never()).send(
            any(),
            any<Hint>()
        )
    }

    private fun assertEnvelopeItemDataForCheckIn(item: SentryEnvelopeItem) {
        val stream = ByteArrayOutputStream()
        val writer = stream.bufferedWriter(Charset.forName("UTF-8"))
        fixture.sentryOptions.serializer.serialize(checkIn, writer)
        val expectedData = stream.toByteArray()
        assertTrue(Arrays.equals(expectedData, item.data))
    }

    @Test
    fun `when captureCheckIn and connection throws, log exception`() {
        val sut = fixture.getSut()

        val exception = IOException("No connection")
        whenever(fixture.transport.send(any(), any())).thenThrow(exception)

        val logger = mock<ILogger>()
        fixture.sentryOptions.setLogger(logger)

        sut.captureCheckIn(checkIn, null, null)

        verify(logger)
            .log(
                SentryLevel.WARNING,
                exception,
                "Capturing check-in %s failed.",
                checkIn.checkInId
            )
    }

    @Test
    fun `when hint is Cached, scope is not applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL

        val hints = HintUtils.createWithTypeCheckHint(mock<Cached>())
        sut.captureEvent(event, scope, hints)

        assertNotEquals(scope.level, event.level)
    }

    @Test
    fun `when hint is not Cached, scope is applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL

        val hints = HintUtils.createWithTypeCheckHint(Object())
        sut.captureEvent(event, scope, hints)

        assertEquals(scope.level, event.level)
    }

    @Test
    fun `when hint is ApplyScopeData, scope is applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL

        val hints = HintUtils.createWithTypeCheckHint(mock<ApplyScopeData>())
        sut.captureEvent(event, scope, hints)

        assertEquals(scope.level, event.level)
    }

    @Test
    fun `when hint is Cached but also ApplyScopeData, scope is applied`() {
        val sut = fixture.getSut()

        val event = SentryEvent()
        val scope = Scope(SentryOptions())
        scope.level = SentryLevel.FATAL

        val hints = HintUtils.createWithTypeCheckHint(CustomCachedApplyScopeDataHint())
        sut.captureEvent(event, scope, hints)

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

        SentryClient(sentryOptions)

        assertEquals(transportFactory, sentryOptions.transportFactory)
    }

    @Test
    fun `when transport gate is set on options, it should use the custom transport gate`() {
        val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
        }
        val transportGate = CustomTransportGate()
        sentryOptions.setTransportGate(transportGate)

        SentryClient(sentryOptions)

        assertEquals(transportGate, sentryOptions.transportGate)
    }

    @Test
    fun `when transport gate is null, it should init an always on transport gate`() {
        val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
        }

        SentryClient(sentryOptions)

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

        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())
        verify(processor).process(eq(transaction), anyOrNull())
    }

    @Test
    fun `transaction dropped by global event processor is recorded`() {
        fixture.sentryOptions.addEventProcessor(DropEverythingEventProcessor())

        val transaction = SentryTransaction(fixture.sentryTracer)

        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2)
            )
        )
    }

    @Test
    fun `transaction dropped by ignoredTransactions is recorded`() {
        fixture.sentryOptions.setIgnoredTransactions(listOf("a-transaction"))

        val transaction = SentryTransaction(fixture.sentryTracer)

        val eventId =
            fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        verify(fixture.transport, never()).send(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2)
            )
        )

        assertEquals(SentryId.EMPTY_ID, eventId)
    }

    @Test
    fun `transaction dropped by ignoredTransactions with regex is recorded`() {
        fixture.sentryOptions.setIgnoredTransactions(listOf("a.*action"))

        val transaction = SentryTransaction(fixture.sentryTracer)

        val eventId =
            fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        verify(fixture.transport, never()).send(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2)
            )
        )

        assertEquals(SentryId.EMPTY_ID, eventId)
    }

    @Test
    fun `backfillable events are only wired through backfilling processors`() {
        val backfillingProcessor = mock<BackfillingEventProcessor>()
        val nonBackfillingProcessor = mock<EventProcessor>()
        fixture.sentryOptions.addEventProcessor(backfillingProcessor)
        fixture.sentryOptions.addEventProcessor(nonBackfillingProcessor)

        val event = SentryEvent()
        val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

        fixture.getSut().captureEvent(event, hint)

        verify(backfillingProcessor).process(eq(event), eq(hint))
        verify(nonBackfillingProcessor, never()).process(any<SentryEvent>(), anyOrNull())
    }

    @Test
    fun `scope is not applied to backfillable events`() {
        val event = SentryEvent()
        val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
        val scope = createScope()

        fixture.getSut().captureEvent(event, scope, hint)

        assertNull(event.user)
        assertNull(event.level)
        assertNull(event.breadcrumbs)
        assertNull(event.request)
    }

    @Test
    fun `tracingContext values are derived from backfillable events`() {
        val traceId = SentryId(UUID.randomUUID())
        val event = SentryEvent().apply {
            environment = "release"
            release = "io.sentry.samples@22.1.1"
            contexts[Contexts.REPLAY_ID] = "64cf554cc8d74c6eafa3e08b7c984f6d"
            contexts.setTrace(SpanContext(traceId, SpanId(), "ui.load", null, null))
            transaction = "MainActivity"
        }
        val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
        val scope = createScope()

        fixture.getSut().captureEvent(event, scope, hint)

        verify(fixture.transport).send(
            check {
                assertEquals("release", it.header.traceContext!!.environment)
                assertEquals("io.sentry.samples@22.1.1", it.header.traceContext!!.release)
                assertEquals(traceId, it.header.traceContext!!.traceId)
                assertEquals("MainActivity", it.header.traceContext!!.transaction)
                assertEquals(SentryId("64cf554cc8d74c6eafa3e08b7c984f6d"), it.header.traceContext!!.replayId)
            },
            anyOrNull()
        )
    }

    @Test
    fun `non-backfillable events are only wired through regular processors`() {
        val backfillingProcessor = mock<BackfillingEventProcessor>()
        val nonBackfillingProcessor = mock<EventProcessor>()
        fixture.sentryOptions.addEventProcessor(backfillingProcessor)
        fixture.sentryOptions.addEventProcessor(nonBackfillingProcessor)

        val event = SentryEvent()

        fixture.getSut().captureEvent(event)

        verify(backfillingProcessor, never()).process(any<SentryEvent>(), anyOrNull())
        verify(nonBackfillingProcessor).process(eq(event), anyOrNull())
    }

    @Test
    fun `transaction dropped by beforeSendTransaction is recorded`() {
        fixture.sentryOptions.setBeforeSendTransaction { transaction, hint ->
            null
        }

        val transaction = SentryTransaction(fixture.sentryTracer)

        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Span.category, 2)
            )
        )
    }

    @Test
    fun `transaction dropped by scope event processor is recorded`() {
        val transaction = SentryTransaction(fixture.sentryTracer)
        val scope = createScope()
        scope.addEventProcessor(DropEverythingEventProcessor())

        val sut = fixture.getSut()

        sut.captureTransaction(transaction, scope, null)

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2)
            )
        )
    }

    @Test
    fun `span dropped by event processor is recorded`() {
        fixture.sentryTracer.startChild("dropped span", "span1").finish()
        fixture.sentryTracer.startChild("dropped span", "span2").finish()
        val transaction = SentryTransaction(fixture.sentryTracer)
        val scope = createScope()
        scope.addEventProcessor(object : EventProcessor {
            override fun process(transaction: SentryTransaction, hint: Hint): SentryTransaction? {
                // we are removing span1 and a-span from the fixture
                transaction.spans.removeIf { it.description != "span2" }
                return transaction
            }
        })

        fixture.getSut().captureTransaction(transaction, scope, null)

        verify(fixture.transport).send(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2)
            )
        )
    }

    @Test
    fun `event dropped by global event processor is recorded`() {
        fixture.sentryOptions.addEventProcessor(DropEverythingEventProcessor())

        val event = SentryEvent()

        fixture.getSut().captureEvent(event)

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `event dropped by scope event processor is recorded`() {
        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 0))
        )
        val event = SentryEvent()
        val scope = createScope()
        scope.addEventProcessor(DropEverythingEventProcessor())

        val sut = fixture.getSut()

        sut.captureEvent(event, scope)

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `when beforeSendTransaction is set, callback is invoked`() {
        var invoked = false
        fixture.sentryOptions.setBeforeSendTransaction { t, _ -> invoked = true; t }

        val transaction = SentryTransaction(fixture.sentryTracer)
        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        assertTrue(invoked)
    }

    @Test
    fun `when beforeSendTransaction is returns null, event is dropped`() {
        fixture.sentryOptions.setBeforeSendTransaction { _: SentryTransaction, _: Any? -> null }

        val transaction = SentryTransaction(fixture.sentryTracer)
        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        verify(fixture.transport, never()).send(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Span.category, 2)
            )
        )
    }

    @Test
    fun `when beforeSendTransaction returns new instance, new instance is sent`() {
        val expected = SentryTransaction(fixture.sentryTracer).apply {
            setTag("test", "test")
        }
        fixture.sentryOptions.setBeforeSendTransaction { _, _ -> expected }

        val transaction = SentryTransaction(fixture.sentryTracer)
        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        verify(fixture.transport).send(
            check {
                val tx = getTransactionFromData(it.items.first().data)
                assertEquals("test", tx.tags!!["test"])
            },
            anyOrNull()
        )
        verifyNoMoreInteractions(fixture.transport)
    }

    @Test
    fun `when beforeSendTransaction throws an exception, transaction is dropped`() {
        val exception = Exception("test")

        exception.stackTrace.toString()
        fixture.sentryOptions.setBeforeSendTransaction { _, _ -> throw exception }

        val transaction = SentryTransaction(fixture.sentryTracer)
        val id = fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        assertEquals(SentryId.EMPTY_ID, id)

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Span.category, 2)
            )
        )
    }

    @Test
    fun `when beforeSendTransaction drops a span, dropped span is recorded`() {
        fixture.sentryTracer.startChild("dropped span", "span1").finish()
        fixture.sentryTracer.startChild("dropped span", "span2").finish()
        fixture.sentryOptions.setBeforeSendTransaction { t: SentryTransaction, _: Any? ->
            t.apply {
                // we are removing span1 and a-span from the fixture
                spans.removeIf { it.description != "span2" }
            }
        }

        val transaction = SentryTransaction(fixture.sentryTracer)
        fixture.getSut().captureTransaction(transaction, fixture.sentryTracer.traceContext())

        verify(fixture.transport).send(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Span.category, 2)
            )
        )
    }

    @Test
    fun `captureProfileChunk ignores beforeSend`() {
        var invoked = false
        fixture.sentryOptions.setBeforeSendTransaction { t, _ -> invoked = true; t }
        fixture.getSut().captureProfileChunk(fixture.profileChunk, mock())
        assertFalse(invoked)
    }

    @Test
    fun `captureProfileChunk ignores Event Processors`() {
        val mockProcessor = mock<EventProcessor>()
        fixture.sentryOptions.addEventProcessor(mockProcessor)
        fixture.getSut().captureProfileChunk(fixture.profileChunk, mock())
        verifyNoInteractions(mockProcessor)
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
    fun `When event is non handled, mark session as Crashed`() {
        val scope = Scope(fixture.sentryOptions)
        scope.startSession()

        val event = SentryEvent().apply {
            exceptions = createNonHandledException()
        }
        fixture.getSut().updateSessionData(event, Hint(), scope)
        scope.withSession {
            assertEquals(Session.State.Crashed, it!!.status)
        }
    }

    @Test
    fun `When event is non handled, end the session`() {
        val scope = Scope(fixture.sentryOptions)
        scope.startSession()

        val event = SentryEvent().apply {
            exceptions = createNonHandledException()
        }
        fixture.getSut().updateSessionData(event, Hint(), scope)
        scope.withSession {
            assertEquals(Session.State.Crashed, it!!.status)
            assertNotNull(it.duration)
        }
    }

    @Test
    fun `When event is handled, keep level as it is`() {
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            val session = it.current
            val level = session.status
            val event = SentryEvent()
            fixture.getSut().updateSessionData(
                event,
                Hint(),
                scope
            )
            assertEquals(level, session.status)
        }
    }

    @Test
    fun `When event is non handled, increase errorCount`() {
        val scope = Scope(fixture.sentryOptions)
        scope.startSession()
        val event = SentryEvent().apply {
            exceptions = createNonHandledException()
        }
        fixture.getSut().updateSessionData(event, Hint(), scope)
        scope.withSession {
            assertEquals(1, it!!.errorCount())
        }
    }

    @Test
    fun `When event is Errored, increase errorCount`() {
        val scope = Scope(fixture.sentryOptions)
        scope.startSession()
        val exceptions = mutableListOf<SentryException>()
        exceptions.add(SentryException())
        val event = SentryEvent().apply {
            setExceptions(exceptions)
        }
        fixture.getSut().updateSessionData(event, Hint(), scope)
        scope.withSession {
            assertEquals(1, it!!.errorCount())
        }
    }

    @Test
    fun `When event is handled and not errored, do not increase errorsCount`() {
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            val session = it.current
            val errorCount = session.errorCount()
            val event = SentryEvent()
            fixture.getSut().updateSessionData(
                event,
                Hint(),
                scope
            )
            assertEquals(errorCount, session.errorCount())
        }
    }

    @Test
    fun `when event has abnormal hint, sets abnormalMechanism and changes status to abnormal`() {
        val scope = givenScopeWithStartedSession()
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint = HintUtils.createWithTypeCheckHint(AbnormalHint("anr_foreground"))

        fixture.getSut().updateSessionData(event, hint, scope)

        scope.withSession {
            assertEquals(Session.State.Abnormal, it!!.status)
            assertEquals("anr_foreground", it.abnormalMechanism)
        }
    }

    @Test
    fun `when event has abnormal hint, increases errorCrount`() {
        val scope = givenScopeWithStartedSession()
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint = HintUtils.createWithTypeCheckHint(AbnormalHint("anr_foreground"))

        fixture.getSut().updateSessionData(event, hint, scope)

        scope.withSession {
            assertEquals(1, it!!.errorCount())
        }
    }

    @Test
    fun `When event has userAgent, set it into session`() {
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            val event = SentryEvent().apply {
                request = Request().apply {
                    headers = mutableMapOf("user-agent" to "jamesBond")
                }
            }
            fixture.getSut().updateSessionData(
                event,
                Hint(),
                scope
            )
            scope.withSession {
                assertEquals("jamesBond", it!!.userAgent)
            }
        }
    }

    @Test
    fun `When event has no userAgent, keep as it is`() {
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            val session = it.current
            val userAgent = session.userAgent
            val event = SentryEvent().apply {
                request = Request().apply {
                    headers = mutableMapOf()
                }
            }
            fixture.getSut().updateSessionData(
                event,
                Hint(),
                scope
            )
            assertEquals(userAgent, session.userAgent)
        }
    }

    @Test
    fun `When capture an event and there's no session, do nothing`() {
        val scope = Scope(fixture.sentryOptions)
        val event = SentryEvent()
        fixture.getSut().updateSessionData(event, Hint(), scope)
        scope.withSession {
            assertNull(it)
        }
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
            scope.withSession {
                assertEquals(Session.State.Crashed, it!!.status)
                assertEquals(1, it.errorCount())
            }
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
    fun `when session is in terminal state, does not send session update`() {
        val sut = fixture.getSut()

        val event = SentryEvent().apply {
            exceptions = createNonHandledException()
        }
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        scope.withSession { it!!.update(Crashed, null, false) }

        assertNotNull(sessionPair) {
            sut.captureEvent(event, scope, null)
            verify(fixture.transport).send(
                check {
                    assertNull(it.items.find { item -> item.header.type == SentryItemType.Session })
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
            anyOrNull()
        )
    }

    @Test
    fun `when captureTransaction with attachments`() {
        val transaction = SentryTransaction(fixture.sentryTracer)
        fixture.getSut().captureTransaction(transaction, createScopeWithAttachments(), null)

        verifyAttachmentsInEnvelope(transaction.eventId)
        assertFails { verifyProfilingTraceInEnvelope(SentryId(fixture.profilingTraceData.profileId)) }
    }

    @Test
    fun `when captureEnvelope with ProfilingTraceData`() {
        val client = fixture.getSut()
        val options = fixture.sentryOptions
        val envelope = SentryEnvelope.from(options.serializer, fixture.profilingTraceData, options.maxTraceFileSize, options.sdkVersion)
        client.captureEnvelope(envelope)
        verifyProfilingTraceInEnvelope(SentryId(fixture.profilingTraceData.profileId))
    }

    @Test
    fun `when capture profile with empty trace file, profile is not sent`() {
        val client = fixture.getSut()
        val options = fixture.sentryOptions
        val envelope = SentryEnvelope.from(options.serializer, fixture.profilingTraceData, options.maxTraceFileSize, options.sdkVersion)
        client.captureEnvelope(envelope)
        fixture.profilingTraceFile.writeText("")
        assertFails { verifyProfilingTraceInEnvelope(SentryId(fixture.profilingTraceData.profileId)) }
    }

    @Test
    fun `when capture profile with non existing profiling trace file, profile is not sent`() {
        val client = fixture.getSut()
        val options = fixture.sentryOptions
        val envelope = SentryEnvelope.from(options.serializer, fixture.profilingNonExistingTraceData, options.maxTraceFileSize, options.sdkVersion)
        client.captureEnvelope(envelope)
        assertFails { verifyProfilingTraceInEnvelope(SentryId(fixture.profilingNonExistingTraceData.profileId)) }
    }

    @Test
    fun `when captureProfileChunk`() {
        val client = fixture.getSut()
        client.captureProfileChunk(fixture.profileChunk, mock())
        verifyProfileChunkInEnvelope(fixture.profileChunk.chunkId)
    }

    @Test
    fun `when captureProfileChunk with empty trace file, profile chunk is not sent`() {
        val client = fixture.getSut()
        fixture.profilingTraceFile.writeText("")
        client.captureProfileChunk(fixture.profileChunk, mock())
        assertFails { verifyProfilingTraceInEnvelope(fixture.profileChunk.chunkId) }
    }

    @Test
    fun `when captureProfileChunk with non existing profiling trace file, profile chunk is not sent`() {
        val client = fixture.getSut()
        fixture.profilingTraceFile.delete()
        client.captureProfileChunk(fixture.profileChunk, mock())
        assertFails { verifyProfilingTraceInEnvelope(fixture.profileChunk.chunkId) }
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
            anyOrNull()
        )
    }

    @Test
    fun `when captureTransaction with scope, transaction should use user data`() {
        val scopes: IScopes = mock()
        whenever(scopes.options).thenReturn(SentryOptions())
        val transaction = SentryTransaction(SentryTracer(TransactionContext("tx", "op"), scopes))
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
        val transaction = SentryTracer(TransactionContext("a-transaction", "op"), fixture.scopes)
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
                assertNotNull(it.header.traceContext) {
                    assertEquals(transaction.spanContext.traceId, it.traceId)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `when scope does not have an active transaction, trace state is set on the envelope from scope`() {
        val sut = fixture.getSut()
        val scope = createScope()
        sut.captureEvent(SentryEvent(), scope)
        verify(fixture.transport).send(
            check {
                assertNotNull(it.header.traceContext)
                assertEquals(scope.propagationContext.traceId, it.header.traceContext?.traceId)
            },
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is captured, trace state is set on the envelope`() {
        val sut = fixture.getSut()
        val transaction = SentryTransaction(fixture.sentryTracer)
        val traceContext = fixture.sentryTracer.traceContext()
        sut.captureTransaction(transaction, traceContext)
        verify(fixture.transport).send(
            check {
                assertEquals(traceContext, it.header.traceContext)
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
        sut.captureTransaction(transaction, fixture.sentryTracer.traceContext())
        assertEquals("optionsRelease", transaction.release)
        assertEquals("optionsEnvironment", transaction.environment)
    }

    @Test
    fun `when transaction has environment and release set, and the environment is set on options, options values are not applied to transactions`() {
        fixture.sentryOptions.release = "optionsRelease"
        fixture.sentryOptions.environment = "optionsEnvironment"
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
        val transaction = SentryTransaction(sentryTracer)
        transaction.release = "transactionRelease"
        transaction.environment = "transactionEnvironment"
        sut.captureTransaction(transaction, sentryTracer.traceContext())
        assertEquals("transactionRelease", transaction.release)
        assertEquals("transactionEnvironment", transaction.environment)
    }

    @Test
    fun `when transaction does not have SDK version set, and the SDK version is set on options, options values are applied to transactions`() {
        fixture.sentryOptions.sdkVersion = SdkVersion("sdk.name", "version")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
        val transaction = SentryTransaction(sentryTracer)
        sut.captureTransaction(transaction, sentryTracer.traceContext())
        assertEquals(fixture.sentryOptions.sdkVersion, transaction.sdk)
    }

    @Test
    fun `when transaction has SDK version set, and the SDK version is set on options, options values are not applied to transactions`() {
        fixture.sentryOptions.sdkVersion = SdkVersion("sdk.name", "version")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
        val transaction = SentryTransaction(sentryTracer)
        val sdkVersion = SdkVersion("transaction.sdk.name", "version")
        transaction.sdk = sdkVersion
        sut.captureTransaction(transaction, sentryTracer.traceContext())
        assertEquals(sdkVersion, transaction.sdk)
    }

    @Test
    fun `when transaction does not have tags, and tags are set on options, options values are applied to transactions`() {
        fixture.sentryOptions.setTag("tag1", "value1")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
        val transaction = SentryTransaction(sentryTracer)
        sut.captureTransaction(transaction, sentryTracer.traceContext())
        assertEquals(mapOf("tag1" to "value1"), transaction.tags)
    }

    @Test
    fun `when transaction has tags, and tags are set on options, options tags are added to transactions`() {
        fixture.sentryOptions.setTag("tag1", "value1")
        fixture.sentryOptions.setTag("tag2", "value2")
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
        val transaction = SentryTransaction(sentryTracer)
        transaction.setTag("tag3", "value3")
        transaction.setTag("tag2", "transaction-tag")
        sut.captureTransaction(transaction, sentryTracer.traceContext())
        assertEquals(
            mapOf("tag1" to "value1", "tag2" to "transaction-tag", "tag3" to "value3"),
            transaction.tags
        )
    }

    @Test
    fun `captured transactions without a platform, have the default platform set`() {
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
        val transaction = SentryTransaction(sentryTracer)
        sut.captureTransaction(transaction, sentryTracer.traceContext())
        assertEquals("java", transaction.platform)
    }

    @Test
    fun `captured transactions with a platform, do not get the platform overwritten`() {
        val sut = fixture.getSut()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), fixture.scopes)
        val transaction = SentryTransaction(sentryTracer)
        transaction.platform = "abc"
        sut.captureTransaction(transaction, sentryTracer.traceContext())
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
    fun `when event message matches string in ignoredErrors, capturing event does not send it`() {
        fixture.sentryOptions.addIgnoredError("hello")
        val sut = fixture.getSut()
        val event = SentryEvent()
        val message = Message()
        message.message = "hello"
        event.setMessage(message)
        sut.captureEvent(event)
        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when event message matches regex pattern in ignoredErrors, capturing event does not send it`() {
        fixture.sentryOptions.addIgnoredError("hello .*")
        val sut = fixture.getSut()
        val event = SentryEvent()
        val message = Message()
        message.message = "hello world"
        event.setMessage(message)
        sut.captureEvent(event)
        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when event message does not match regex pattern in ignoredErrors, capturing event sends it`() {
        fixture.sentryOptions.addIgnoredError("hello .*")
        val sut = fixture.getSut()
        val event = SentryEvent()
        val message = Message()
        message.message = "test"
        event.setMessage(message)
        sut.captureEvent(event)
        verify(fixture.transport).send(any(), anyOrNull())
    }

    @Test
    fun `when exception message matches regex pattern in ignoredErrors, capturing event does not send it`() {
        fixture.sentryOptions.addIgnoredError(".*hello .*")
        val sut = fixture.getSut()
        sut.captureException(RuntimeException("hello world"))
        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when class matches regex pattern in ignoredErrors, capturing event does not send it`() {
        fixture.sentryOptions.addIgnoredError("java\\.lang\\..*")
        val sut = fixture.getSut()
        sut.captureException(RuntimeException("hello world"))
        verify(fixture.transport, never()).send(any(), anyOrNull())
    }

    @Test
    fun `when ignoredExceptionsForType and ignoredErrors are not explicitly specified, capturing event sends event`() {
        val sut = fixture.getSut()
        sut.captureException(RuntimeException("test"))
        verify(fixture.transport).send(any(), anyOrNull())
    }

    @Test
    fun `screenshot is added to the envelope from the hint`() {
        val sut = fixture.getSut()
        val attachment = Attachment.fromScreenshot(byteArrayOf())
        val hint = Hint().also { it.screenshot = attachment }

        sut.captureEvent(SentryEvent(), hint)

        verify(fixture.transport).send(
            check { envelope ->
                val screenshot = envelope.items.last()
                assertNotNull(screenshot) {
                    assertEquals(attachment.filename, screenshot.header.fileName)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `screenshot is dropped from hint via before send`() {
        fixture.sentryOptions.beforeSend = CustomBeforeSendCallback()
        val sut = fixture.getSut()
        val attachment = Attachment.fromScreenshot(byteArrayOf())
        val hint = Hint().also { it.screenshot = attachment }

        sut.captureEvent(SentryEvent(), hint)

        verify(fixture.transport).send(
            check { envelope ->
                assertEquals(1, envelope.items.count())
            },
            anyOrNull()
        )
    }

    @Test
    fun `view hierarchy is added to the envelope from the hint`() {
        val sut = fixture.getSut()
        val attachment = Attachment.fromViewHierarchy(ViewHierarchy("android_view_system", emptyList()))
        val hint = Hint().also { it.viewHierarchy = attachment }

        sut.captureEvent(SentryEvent(), hint)

        verify(fixture.transport).send(
            check { envelope ->
                val viewHierarchy = envelope.items.last()
                assertNotNull(viewHierarchy) {
                    assertEquals(attachment.filename, viewHierarchy.header.fileName)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `view hierarchy is dropped from hint via before send`() {
        fixture.sentryOptions.beforeSend = CustomBeforeSendCallback()
        val sut = fixture.getSut()
        val attachment = Attachment.fromViewHierarchy(ViewHierarchy("android_view_system", emptyList()))
        val hint = Hint().also { it.viewHierarchy = attachment }

        sut.captureEvent(SentryEvent(), hint)

        verify(fixture.transport).send(
            check { envelope ->
                assertEquals(1, envelope.items.count())
            },
            anyOrNull()
        )
    }

    @Test
    fun `thread dump is added to the envelope from the hint`() {
        val sut = fixture.getSut()
        val attachment = Attachment.fromThreadDump(byteArrayOf())
        val hint = Hint().also { it.threadDump = attachment }

        sut.captureEvent(SentryEvent(), hint)

        verify(fixture.transport).send(
            check { envelope ->
                val threadDump = envelope.items.last()
                assertNotNull(threadDump) {
                    assertEquals(attachment.filename, threadDump.header.fileName)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `thread dump is dropped from hint via before send`() {
        fixture.sentryOptions.beforeSend = CustomBeforeSendCallback()
        val sut = fixture.getSut()
        val attachment = Attachment.fromThreadDump(byteArrayOf())
        val hint = Hint().also { it.threadDump = attachment }

        sut.captureEvent(SentryEvent(), hint)

        verify(fixture.transport).send(
            check { envelope ->
                assertEquals(1, envelope.items.count())
            },
            anyOrNull()
        )
    }

    @Test
    fun `capturing an error updates session and sends event + session`() {
        val sut = fixture.getSut()
        val scope = givenScopeWithStartedSession()

        sut.captureEvent(SentryEvent().apply { exceptions = createHandledException() }, scope)

        thenSessionIsErrored(scope)
        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1)
    }

    @Test
    fun `dropping a captured error from beforeSend has no effect on session and does not send anything`() {
        val sut = fixture.getSut { options ->
            options.beforeSend = SentryOptions.BeforeSendCallback { _, _ -> null }
        }
        val scope = givenScopeWithStartedSession()

        sut.captureEvent(SentryEvent().apply { exceptions = createHandledException() }, scope)

        thenSessionIsStillOK(scope)
        thenNothingIsSent()
    }

    @Test
    fun `dropping a captured error from eventProcessor has no effect on session and does not send anything`() {
        val sut = fixture.getSut { options ->
            options.addEventProcessor(DropEverythingEventProcessor())
        }
        val scope = givenScopeWithStartedSession()

        sut.captureEvent(SentryEvent().apply { exceptions = createHandledException() }, scope)

        thenSessionIsStillOK(scope)
        thenNothingIsSent()
    }

    @Test
    fun `dropping a captured error via sampling updates the session and only sends the session for a new session`() {
        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
        }
        val scope = givenScopeWithStartedSession()

        sut.captureEvent(SentryEvent().apply { exceptions = createHandledException() }, scope)

        thenSessionIsErrored(scope)
        thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 1)
    }

    @Test
    fun `dropping a captured error via sampling updates the session and does not send anything for an errored session`() {
        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
        }
        val scope = givenScopeWithStartedSession(errored = true)

        sut.captureEvent(SentryEvent().apply { exceptions = createHandledException() }, scope)

        thenSessionIsErrored(scope)
        thenNothingIsSent()
    }

    @Test
    fun `dropping a captured error via sampling updates the session and does not send anything for a crashed session`() {
        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
        }
        val scope = givenScopeWithStartedSession(crashed = true)

        sut.captureEvent(SentryEvent().apply { exceptions = createHandledException() }, scope)

        thenSessionIsCrashed(scope)
        thenNothingIsSent()
    }

    @Test
    fun `dropping a captured crash via sampling updates the session and only sends the session for a new session`() {
        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
        }
        val scope = givenScopeWithStartedSession()

        sut.captureEvent(SentryEvent().apply { exceptions = createNonHandledException() }, scope)

        thenSessionIsCrashed(scope)
        thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 1)
    }

    @Test
    fun `dropping a captured crash via sampling updates the session and sends the session for an errored session`() {
        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
        }
        val scope = givenScopeWithStartedSession(errored = true)

        sut.captureEvent(SentryEvent().apply { exceptions = createNonHandledException() }, scope)

        thenSessionIsCrashed(scope)
        thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 1)
    }

    @Test
    fun `dropping a captured crash via sampling updates the session and does not send anything for a crashed session`() {
        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
        }
        val scope = givenScopeWithStartedSession(crashed = true)

        sut.captureEvent(SentryEvent().apply { exceptions = createNonHandledException() }, scope)

        thenSessionIsCrashed(scope)
        thenNothingIsSent()
    }

    @Test
    fun `ignored exceptions are checked before other filter mechanisms`() {
        val beforeSendMock = mock<SentryOptions.BeforeSendCallback>()
        val scopedEventProcessorMock = mock<EventProcessor>()
        val globalEventProcessorMock = mock<EventProcessor>()

        whenever(scopedEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).thenReturn(null)
        whenever(globalEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).thenReturn(null)
        whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
            options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
            options.beforeSend = beforeSendMock
            options.addEventProcessor(globalEventProcessorMock)
        }
        val scope = givenScopeWithStartedSession()
        scope.addEventProcessor(scopedEventProcessorMock)

        sut.captureException(NegativeArraySizeException(), scope)

        verify(scopedEventProcessorMock, never()).process(any<SentryEvent>(), anyOrNull())
        verify(globalEventProcessorMock, never()).process(any<SentryEvent>(), anyOrNull())
        verify(beforeSendMock, never()).execute(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `sampling is last filter mechanism`() {
        val beforeSendMock = mock<SentryOptions.BeforeSendCallback>()
        val scopedEventProcessorMock = mock<EventProcessor>()
        val globalEventProcessorMock = mock<EventProcessor>()

        whenever(scopedEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).doAnswer { it.arguments.first() as SentryEvent }
        whenever(globalEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).doAnswer { it.arguments.first() as SentryEvent }
        whenever(beforeSendMock.execute(any(), anyOrNull())).doAnswer { it.arguments.first() as SentryEvent }

        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
            options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
            options.beforeSend = beforeSendMock
            options.addEventProcessor(globalEventProcessorMock)
        }
        val scope = givenScopeWithStartedSession()
        scope.addEventProcessor(scopedEventProcessorMock)

        sut.captureException(IllegalStateException(), scope)

        val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

        order.verify(scopedEventProcessorMock, times(1)).process(any<SentryEvent>(), anyOrNull())
        order.verify(globalEventProcessorMock, times(1)).process(any<SentryEvent>(), anyOrNull())
        order.verify(beforeSendMock, times(1)).execute(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `filter mechanism order check for beforeSend`() {
        val beforeSendMock = mock<SentryOptions.BeforeSendCallback>()
        val scopedEventProcessorMock = mock<EventProcessor>()
        val globalEventProcessorMock = mock<EventProcessor>()

        whenever(scopedEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).doAnswer { it.arguments.first() as SentryEvent }
        whenever(globalEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).doAnswer { it.arguments.first() as SentryEvent }
        whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
            options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
            options.beforeSend = beforeSendMock
            options.addEventProcessor(globalEventProcessorMock)
        }
        val scope = givenScopeWithStartedSession()
        scope.addEventProcessor(scopedEventProcessorMock)

        sut.captureException(IllegalStateException(), scope)

        val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

        order.verify(scopedEventProcessorMock, times(1)).process(any<SentryEvent>(), anyOrNull())
        order.verify(globalEventProcessorMock, times(1)).process(any<SentryEvent>(), anyOrNull())
        order.verify(beforeSendMock, times(1)).execute(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `filter mechanism order check for scoped eventProcessor`() {
        val beforeSendMock = mock<SentryOptions.BeforeSendCallback>()
        val scopedEventProcessorMock = mock<EventProcessor>()
        val globalEventProcessorMock = mock<EventProcessor>()

        whenever(scopedEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).thenReturn(null)
        whenever(globalEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).thenReturn(null)
        whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
            options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
            options.beforeSend = beforeSendMock
            options.addEventProcessor(globalEventProcessorMock)
        }
        val scope = givenScopeWithStartedSession()
        scope.addEventProcessor(scopedEventProcessorMock)

        sut.captureException(IllegalStateException(), scope)

        val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

        order.verify(scopedEventProcessorMock, times(1)).process(any<SentryEvent>(), anyOrNull())
        order.verify(globalEventProcessorMock, never()).process(any<SentryEvent>(), anyOrNull())
        order.verify(beforeSendMock, never()).execute(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `filter mechanism order check for global eventProcessor`() {
        val beforeSendMock = mock<SentryOptions.BeforeSendCallback>()
        val scopedEventProcessorMock = mock<EventProcessor>()
        val globalEventProcessorMock = mock<EventProcessor>()

        whenever(scopedEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).doAnswer { it.arguments.first() as SentryEvent }
        whenever(globalEventProcessorMock.process(any<SentryEvent>(), anyOrNull())).thenReturn(null)
        whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

        val sut = fixture.getSut { options ->
            options.sampleRate = 0.000000000001
            options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
            options.beforeSend = beforeSendMock
            options.addEventProcessor(globalEventProcessorMock)
        }
        val scope = givenScopeWithStartedSession()
        scope.addEventProcessor(scopedEventProcessorMock)

        sut.captureException(IllegalStateException(), scope)

        val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

        order.verify(scopedEventProcessorMock, times(1)).process(any<SentryEvent>(), anyOrNull())
        order.verify(globalEventProcessorMock, times(1)).process(any<SentryEvent>(), anyOrNull())
        order.verify(beforeSendMock, never()).execute(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1))
        )
    }

    @Test
    fun `can pass an attachment via hints`() {
        val sut = fixture.getSut()

        sut.captureException(IllegalStateException(), Hint.withAttachment(fixture.attachment))

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 0, attachmentCount = 1)
    }

    @Test
    fun `an attachment passed via hint is used with scope attachments`() {
        val sut = fixture.getSut()

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)
        sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 2)
    }

    @Test
    fun `can add to attachments in beforeSend`() {
        val sut = fixture.getSut { options ->
            options.setBeforeSend { event, hints ->
                assertEquals(listOf(fixture.attachment, fixture.attachment2), hints.attachments)
                hints.addAttachment(fixture.attachment3)
                event
            }
        }

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)
        sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 3)
    }

    @Test
    fun `can replace attachments in beforeSend`() {
        val sut = fixture.getSut { options ->
            options.setBeforeSend { event, hints ->
                hints.replaceAttachments(listOf(fixture.attachment3))
                event
            }
        }

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)
        sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 1)
    }

    @Test
    fun `can add to attachments in eventProcessor`() {
        val sut = fixture.getSut { options ->
            options.addEventProcessor(object : EventProcessor {
                override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
                    assertEquals(listOf(fixture.attachment, fixture.attachment2), hint.attachments)
                    hint.addAttachment(fixture.attachment3)
                    return event
                }

                override fun process(
                    transaction: SentryTransaction,
                    hint: Hint
                ): SentryTransaction? {
                    return transaction
                }
            })
        }

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)
        sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 3)
    }

    @Test
    fun `can replace attachments in eventProcessor`() {
        val sut = fixture.getSut { options ->
            options.addEventProcessor(object : EventProcessor {
                override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
                    hint.replaceAttachments(listOf(fixture.attachment3))
                    return event
                }

                override fun process(
                    transaction: SentryTransaction,
                    hint: Hint
                ): SentryTransaction? {
                    return transaction
                }
            })
        }

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)
        sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 1)
    }

    @Test
    fun `can pass an attachment via hints for transactions`() {
        val sut = fixture.getSut()
        val scope = createScope()

        sut.captureTransaction(
            SentryTransaction(fixture.sentryTracer),
            scope,
            Hint.withAttachment(fixture.attachment)
        )

        thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 0, attachmentCount = 1, transactionCount = 1)
    }

    @Test
    fun `an attachment passed via hint is used with scope attachments for transactions`() {
        val sut = fixture.getSut()

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)

        sut.captureTransaction(
            SentryTransaction(fixture.sentryTracer),
            scope,
            Hint.withAttachment(fixture.attachment)
        )

        thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 0, attachmentCount = 2, transactionCount = 1)
    }

    @Test
    fun `can add to attachments in eventProcessor for transactions`() {
        val sut = fixture.getSut { options ->
            options.addEventProcessor(object : EventProcessor {
                override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
                    return event
                }

                override fun process(
                    transaction: SentryTransaction,
                    hint: Hint
                ): SentryTransaction? {
                    assertEquals(listOf(fixture.attachment, fixture.attachment2), hint.attachments)
                    hint.addAttachment(fixture.attachment3)
                    return transaction
                }
            })
        }

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)

        sut.captureTransaction(
            SentryTransaction(fixture.sentryTracer),
            scope,
            Hint.withAttachment(fixture.attachment)
        )

        thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 0, attachmentCount = 3, transactionCount = 1)
    }

    @Test
    fun `can replace attachments in eventProcessor for transactions`() {
        val sut = fixture.getSut { options ->
            options.addEventProcessor(object : EventProcessor {
                override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
                    return event
                }

                override fun process(
                    transaction: SentryTransaction,
                    hint: Hint
                ): SentryTransaction? {
                    hint.replaceAttachments(listOf(fixture.attachment3))
                    return transaction
                }
            })
        }

        val scope = givenScopeWithStartedSession()
        scope.addAttachment(fixture.attachment2)

        sut.captureTransaction(
            SentryTransaction(fixture.sentryTracer),
            scope,
            Hint.withAttachment(fixture.attachment)
        )

        thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 0, attachmentCount = 1, transactionCount = 1)
    }

    @Test
    fun `passing attachments via hint into breadcrumb ignores them`() {
        val sut = fixture.getSut { options ->
            options.setBeforeBreadcrumb { breadcrumb, hints ->
                breadcrumb
            }
        }

        val scope = givenScopeWithStartedSession()
        scope.addBreadcrumb(Breadcrumb.info("hello from breadcrumb"), Hint.withAttachment(fixture.attachment))

        sut.captureException(IllegalStateException(), scope)

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 0)
    }

    @Test
    fun `adding attachments in beforeBreadcrumb ignores them`() {
        val sut = fixture.getSut { options ->
            options.setBeforeBreadcrumb { breadcrumb, hints ->
                hints.addAttachment(fixture.attachment)
                breadcrumb
            }
        }

        val scope = givenScopeWithStartedSession()
        scope.addBreadcrumb(Breadcrumb.info("hello from breadcrumb"))

        sut.captureException(IllegalStateException(), scope)

        thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 0)
    }

    @Test
    fun `TransactionEnds automatically trigger force-stop of any running transaction`() {
        val sut = fixture.getSut()

        // build up a running transaction
        val spanContext = SpanContext("op.load")
        val transaction = mock<ITransaction>()
        whenever(transaction.name).thenReturn("transaction")
        whenever(transaction.spanContext).thenReturn(spanContext)

        // scope
        val scope = mock<IScope>()
        whenever(scope.transaction).thenReturn(transaction)
        whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
        whenever(scope.extras).thenReturn(emptyMap())
        whenever(scope.contexts).thenReturn(Contexts())
        val scopePropagationContext = PropagationContext()
        whenever(scope.propagationContext).thenReturn(scopePropagationContext)
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())

        val transactionEnd = object : TransactionEnd {}
        val transactionEndHint = HintUtils.createWithTypeCheckHint(transactionEnd)

        sut.captureEvent(SentryEvent(), scope, transactionEndHint)

        verify(transaction).forceFinish(SpanStatus.ABORTED, false, null)
        verify(fixture.transport).send(
            check {
                assertEquals(1, it.items.count())
            },
            any()
        )
    }

    @Test
    fun `when event has DiskFlushNotification, TransactionEnds set transaction id as flushable`() {
        val sut = fixture.getSut()

        // build up a running transaction
        val spanContext = SpanContext("op.load")
        val transaction = mock<ITransaction>()
        whenever(transaction.name).thenReturn("transaction")
        whenever(transaction.eventId).thenReturn(SentryId())
        whenever(transaction.spanContext).thenReturn(spanContext)

        // scope
        val scope = mock<Scope>()
        whenever(scope.transaction).thenReturn(transaction)
        whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
        whenever(scope.extras).thenReturn(emptyMap())
        whenever(scope.contexts).thenReturn(Contexts())
        whenever(scope.replayId).thenReturn(SentryId.EMPTY_ID)
        val scopePropagationContext = PropagationContext()
        whenever(scope.propagationContext).thenReturn(scopePropagationContext)
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())

        var capturedEventId: SentryId? = null
        val transactionEnd = object : TransactionEnd, DiskFlushNotification {
            override fun markFlushed() {}
            override fun isFlushable(eventId: SentryId?): Boolean = true
            override fun setFlushable(eventId: SentryId) {
                capturedEventId = eventId
            }
        }
        val transactionEndHint = HintUtils.createWithTypeCheckHint(transactionEnd)

        sut.captureEvent(SentryEvent(), scope, transactionEndHint)

        assertEquals(transaction.eventId, capturedEventId)
        verify(transaction).forceFinish(SpanStatus.ABORTED, false, transactionEndHint)
        verify(fixture.transport).send(
            check {
                assertEquals(1, it.items.count())
            },
            any()
        )
    }

    @Test
    fun `attaches trace context from span if none present yet`() {
        val sut = fixture.getSut()

        // build up a running transaction
        val spanContext = SpanContext("op.load")
        val transaction = mock<ITransaction>()
        whenever(transaction.name).thenReturn("transaction")
        whenever(transaction.spanContext).thenReturn(spanContext)

        // scope
        val scope = mock<IScope>()
        whenever(scope.transaction).thenReturn(transaction)
        whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
        whenever(scope.extras).thenReturn(emptyMap())
        whenever(scope.contexts).thenReturn(Contexts())
        val scopePropagationContext = PropagationContext()
        whenever(scope.propagationContext).thenReturn(scopePropagationContext)
        whenever(scope.span).thenReturn(transaction)
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())

        val sentryEvent = SentryEvent()
        sut.captureEvent(sentryEvent, scope)

        verify(fixture.transport).send(
            check {
                assertEquals(1, it.items.count())
            },
            any()
        )

        assertEquals(spanContext.traceId, sentryEvent.contexts.trace!!.traceId)
        assertEquals(spanContext.spanId, sentryEvent.contexts.trace!!.spanId)
        assertNotEquals(scopePropagationContext.traceId, sentryEvent.contexts.trace!!.traceId)
        assertNotEquals(scopePropagationContext.spanId, sentryEvent.contexts.trace!!.spanId)
    }

    @Test
    fun `attaches trace context from scope if none present yet and no span on scope`() {
        val sut = fixture.getSut()

        // scope
        val scope = mock<IScope>()
        whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
        whenever(scope.extras).thenReturn(emptyMap())
        whenever(scope.contexts).thenReturn(Contexts())
        whenever(scope.replayId).thenReturn(SentryId())
        val scopePropagationContext = PropagationContext()
        whenever(scope.propagationContext).thenReturn(scopePropagationContext)
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())

        val sentryEvent = SentryEvent()
        sut.captureEvent(sentryEvent, scope)

        verify(fixture.transport).send(
            check {
                assertEquals(1, it.items.count())
            },
            any()
        )

        assertEquals(scopePropagationContext.traceId, sentryEvent.contexts.trace!!.traceId)
        assertEquals(scopePropagationContext.spanId, sentryEvent.contexts.trace!!.spanId)
    }

    @Test
    fun `keeps existing trace context if already present`() {
        val sut = fixture.getSut()

        // build up a running transaction
        val spanContext = SpanContext("op.load")
        val transaction = mock<ITransaction>()
        whenever(transaction.name).thenReturn("transaction")
        whenever(transaction.spanContext).thenReturn(spanContext)

        // scope
        val scope = mock<IScope>()
        whenever(scope.transaction).thenReturn(transaction)
        whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
        whenever(scope.extras).thenReturn(emptyMap())
        whenever(scope.contexts).thenReturn(Contexts())
        val scopePropagationContext = PropagationContext()
        whenever(scope.propagationContext).thenReturn(scopePropagationContext)
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())

        val preExistingSpanContext = SpanContext("op.load")

        val sentryEvent = SentryEvent()
        sentryEvent.contexts.setTrace(preExistingSpanContext)
        sut.captureEvent(sentryEvent, scope)

        verify(fixture.transport).send(
            check {
                assertEquals(1, it.items.count())
            },
            any()
        )

        assertEquals(preExistingSpanContext.traceId, sentryEvent.contexts.trace!!.traceId)
        assertEquals(preExistingSpanContext.spanId, sentryEvent.contexts.trace!!.spanId)
        assertNotEquals(spanContext.traceId, sentryEvent.contexts.trace!!.traceId)
        assertNotEquals(spanContext.spanId, sentryEvent.contexts.trace!!.spanId)
        assertNotEquals(scopePropagationContext.traceId, sentryEvent.contexts.trace!!.traceId)
        assertNotEquals(scopePropagationContext.spanId, sentryEvent.contexts.trace!!.spanId)
    }

    @Test
    fun `uses propagation context on scope for trace header if no transaction is on scope`() {
        val sut = fixture.getSut()

        // scope
        val scope = mock<IScope>()
        whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
        whenever(scope.extras).thenReturn(emptyMap())
        whenever(scope.contexts).thenReturn(Contexts())
        val replayId = SentryId()
        whenever(scope.replayId).thenReturn(replayId)
        val scopePropagationContext = PropagationContext()
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())
        whenever(scope.propagationContext).thenReturn(scopePropagationContext)
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())

        val sentryEvent = SentryEvent()
        sut.captureEvent(sentryEvent, scope)

        verify(fixture.transport).send(
            check {
                assertNotNull(it.header.traceContext)
                assertEquals(scopePropagationContext.traceId, it.header.traceContext!!.traceId)
                assertEquals(replayId, it.header.traceContext!!.replayId)
            },
            any()
        )
    }

    @Test
    fun `uses trace context on transaction for trace header if a transaction is on scope`() {
        val sut = fixture.getSut()

        // build up a running transaction
        val spanContext = SpanContext("op.load")
        val transaction = mock<ITransaction>()
        whenever(transaction.name).thenReturn("transaction")
        whenever(transaction.spanContext).thenReturn(spanContext)
        val transactionTraceContext = TraceContext(SentryId(), "pubkey")
        whenever(transaction.traceContext()).thenReturn(transactionTraceContext)

        // scope
        val scope = mock<IScope>()
        whenever(scope.transaction).thenReturn(transaction)
        whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
        whenever(scope.extras).thenReturn(emptyMap())
        whenever(scope.contexts).thenReturn(Contexts())
        val scopePropagationContext = PropagationContext()
        whenever(scope.propagationContext).thenReturn(scopePropagationContext)
        doAnswer { (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext); scopePropagationContext }.whenever(scope).withPropagationContext(any())

        val preExistingSpanContext = SpanContext("op.load")

        val sentryEvent = SentryEvent()
        sentryEvent.contexts.setTrace(preExistingSpanContext)
        sut.captureEvent(sentryEvent, scope)

        verify(fixture.transport).send(
            check {
                assertNotNull(it.header.traceContext)
                assertEquals(transactionTraceContext.traceId, it.header.traceContext!!.traceId)
            },
            any()
        )
    }

    @Test
    fun `beforeEnvelopeCallback is executed`() {
        var beforeEnvelopeCalled = false
        val sut = fixture.getSut { options ->
            options.beforeEnvelopeCallback =
                SentryOptions.BeforeEnvelopeCallback { _, _ -> beforeEnvelopeCalled = true }
        }

        sut.captureEvent(SentryEvent(), Hint())

        assertTrue(beforeEnvelopeCalled)
    }

    @Test
    fun `beforeEnvelopeCallback may fail, but the transport is still sends the envelope `() {
        val sut = fixture.getSut { options ->
            options.beforeEnvelopeCallback =
                SentryOptions.BeforeEnvelopeCallback { _, _ ->
                    RuntimeException("hook failed")
                }
        }

        sut.captureEvent(SentryEvent(), Hint())
        verify(fixture.transport).send(anyOrNull(), anyOrNull())
    }

    @Test
    fun `when captureReplayEvent, envelope is sent`() {
        val sut = fixture.getSut()
        val replayEvent = createReplayEvent()

        sut.captureReplayEvent(replayEvent, null, null)

        verify(fixture.transport).send(
            check { actual ->
                assertEquals(replayEvent.eventId, actual.header.eventId)
                assertEquals(fixture.sentryOptions.sdkVersion, actual.header.sdkVersion)

                assertEquals(1, actual.items.count())
                val item = actual.items.first()
                assertEquals(SentryItemType.ReplayVideo, item.header.type)

                val unpacker = MessagePack.newDefaultUnpacker(item.data)
                val mapSize = unpacker.unpackMapHeader()
                assertEquals(1, mapSize)
            },
            any<Hint>()
        )
    }

    @Test
    fun `when captureReplayEvent with recording, adds it to payload`() {
        val sut = fixture.getSut()
        val replayEvent = createReplayEvent()

        val hint = Hint().apply { replayRecording = createReplayRecording() }
        sut.captureReplayEvent(replayEvent, null, hint)

        verify(fixture.transport).send(
            check { actual ->
                assertEquals(replayEvent.eventId, actual.header.eventId)
                assertEquals(fixture.sentryOptions.sdkVersion, actual.header.sdkVersion)

                assertEquals(1, actual.items.count())
                val item = actual.items.first()
                assertEquals(SentryItemType.ReplayVideo, item.header.type)

                val unpacker = MessagePack.newDefaultUnpacker(item.data)
                val mapSize = unpacker.unpackMapHeader()
                assertEquals(2, mapSize)
            },
            any<Hint>()
        )
    }

    @Test
    fun `when captureReplayEvent, omits breadcrumbs and extras from scope`() {
        val sut = fixture.getSut()
        val replayEvent = createReplayEvent()

        sut.captureReplayEvent(replayEvent, createScope(), null)

        verify(fixture.transport).send(
            check { actual ->
                val item = actual.items.first()

                val unpacker = MessagePack.newDefaultUnpacker(item.data)
                val mapSize = unpacker.unpackMapHeader()
                for (i in 0 until mapSize) {
                    val key = unpacker.unpackString()
                    when (key) {
                        SentryItemType.ReplayEvent.itemType -> {
                            val replayEventLength = unpacker.unpackBinaryHeader()
                            val replayEventBytes = unpacker.readPayload(replayEventLength)
                            val actualReplayEvent = fixture.sentryOptions.serializer.deserialize(
                                InputStreamReader(replayEventBytes.inputStream()),
                                SentryReplayEvent::class.java
                            )
                            // sanity check
                            assertEquals("id", actualReplayEvent!!.user!!.id)

                            assertNull(actualReplayEvent.breadcrumbs)
                            assertNull(actualReplayEvent.extras)
                        }
                    }
                }
            },
            any<Hint>()
        )
    }

    @Test
    fun `when replay event is dropped, captures client report with datacategory replay`() {
        fixture.sentryOptions.addEventProcessor(DropEverythingEventProcessor())
        val sut = fixture.getSut()
        val replayEvent = createReplayEvent()

        sut.captureReplayEvent(replayEvent, createScope(), null)

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Replay.category, 1))
        )
    }

    @Test
    fun `calls captureReplay on replay controller for error events`() {
        var called = false
        fixture.sentryOptions.setReplayController(object : ReplayController by NoOpReplayController.getInstance() {
            override fun captureReplay(isTerminating: Boolean?) {
                called = true
            }
        })
        val sut = fixture.getSut()

        sut.captureEvent(SentryEvent().apply { exceptions = listOf(SentryException()) })
        assertTrue(called)
    }

    @Test
    fun `calls captureReplay on replay controller for crash events and sets isTerminating`() {
        var terminated: Boolean? = false
        fixture.sentryOptions.setReplayController(object : ReplayController by NoOpReplayController.getInstance() {
            override fun captureReplay(isTerminating: Boolean?) {
                terminated = isTerminating
            }
        })
        val sut = fixture.getSut()

        sut.captureEvent(
            SentryEvent().apply {
                exceptions = listOf(
                    SentryException().apply {
                        mechanism = Mechanism().apply { isHandled = false }
                    }
                )
            }
        )
        assertTrue(terminated == true)
    }

    @Test
    fun `cleans up replay folder for Backfillable replay events`() {
        val dir = File(tmpDir.newFolder().absolutePath)
        val sut = fixture.getSut()
        val replayEvent = createReplayEvent().apply {
            videoFile = File(dir, "hello.txt").apply { writeText("hello") }
        }

        sut.captureReplayEvent(replayEvent, createScope(), HintUtils.createWithTypeCheckHint(BackfillableHint()))

        verify(fixture.transport).send(
            check { actual ->
                val item = actual.items.first()
                item.data
                assertFalse(dir.exists())
            },
            any<Hint>()
        )
    }

    @Test
    fun `does not captureReplay for backfillable events`() {
        var called = false
        fixture.sentryOptions.setReplayController(object : ReplayController by NoOpReplayController.getInstance() {
            override fun captureReplay(isTerminating: Boolean?) {
                called = true
            }
        })
        val sut = fixture.getSut()

        sut.captureEvent(
            SentryEvent().apply {
                exceptions = listOf(
                    SentryException().apply {
                        mechanism = Mechanism().apply { isHandled = false }
                    }
                )
            },
            HintUtils.createWithTypeCheckHint(BackfillableHint())
        )
        assertFalse(called)
    }

    @Test
    fun `when beforeSendReplay is set, callback is invoked`() {
        var invoked = false
        fixture.sentryOptions.setBeforeSendReplay { replay: SentryReplayEvent, _: Hint -> invoked = true; replay }

        fixture.getSut().captureReplayEvent(SentryReplayEvent(), Scope(fixture.sentryOptions), Hint())

        assertTrue(invoked)
    }

    @Test
    fun `when beforeSendReplay returns null, event is dropped`() {
        fixture.sentryOptions.setBeforeSendReplay { replay: SentryReplayEvent, _: Hint -> null }

        fixture.getSut().captureReplayEvent(SentryReplayEvent(), Scope(fixture.sentryOptions), Hint())

        verify(fixture.transport, never()).send(any(), anyOrNull())

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Replay.category, 1)
            )
        )
    }

    @Test
    fun `when beforeSendReplay returns new instance, new instance is sent`() {
        val expected = SentryReplayEvent().apply { tags = mapOf("test" to "test") }
        fixture.sentryOptions.setBeforeSendReplay { _, _ -> expected }

        fixture.getSut().captureReplayEvent(SentryReplayEvent(), Scope(fixture.sentryOptions), Hint())

        verify(fixture.transport).send(
            check {
                val replay = getReplayFromData(it.items.first().data)
                assertEquals("test", replay!!.tags!!["test"])
            },
            anyOrNull()
        )
        verifyNoMoreInteractions(fixture.transport)
    }

    @Test
    fun `when beforeSendReplay throws an exception, replay is dropped`() {
        val exception = Exception("test")

        exception.stackTrace.toString()
        fixture.sentryOptions.setBeforeSendReplay { _, _ -> throw exception }

        val id = fixture.getSut().captureReplayEvent(SentryReplayEvent(), Scope(fixture.sentryOptions), Hint())

        assertEquals(SentryId.EMPTY_ID, id)

        assertClientReport(
            fixture.sentryOptions.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Replay.category, 1)
            )
        )
    }

    private fun givenScopeWithStartedSession(errored: Boolean = false, crashed: Boolean = false): IScope {
        val scope = createScope(fixture.sentryOptions)
        scope.startSession()

        if (errored) {
            scope.withSession { it?.update(Session.State.Ok, "some-user-agent", true) }
        }

        if (crashed) {
            scope.withSession { it?.update(Session.State.Crashed, "some-user-agent", true) }
        }

        return scope
    }

    private fun thenNothingIsSent() {
        verify(fixture.transport, never()).send(anyOrNull(), anyOrNull())
    }

    private fun thenEnvelopeIsSentWith(eventCount: Int, sessionCount: Int, attachmentCount: Int = 0, transactionCount: Int = 0) {
        val argumentCaptor = argumentCaptor<SentryEnvelope>()
        verify(fixture.transport, times(1)).send(argumentCaptor.capture(), anyOrNull())

        val envelope = argumentCaptor.firstValue
        val envelopeItemTypes = envelope.items.map { it.header.type }
        assertEquals(eventCount, envelopeItemTypes.count { it == SentryItemType.Event })
        assertEquals(sessionCount, envelopeItemTypes.count { it == SentryItemType.Session })
        assertEquals(attachmentCount, envelopeItemTypes.count { it == SentryItemType.Attachment })
        assertEquals(transactionCount, envelopeItemTypes.count { it == SentryItemType.Transaction })
    }

    private fun thenSessionIsStillOK(scope: IScope) {
        val sessionAfterCapture = scope.withSession { }!!
        assertEquals(0, sessionAfterCapture.errorCount())
        assertEquals(Session.State.Ok, sessionAfterCapture.status)
    }

    private fun thenSessionIsErrored(scope: IScope) {
        val sessionAfterCapture = scope.withSession { }!!
        assertTrue(sessionAfterCapture.errorCount() > 0)
        assertEquals(Session.State.Ok, sessionAfterCapture.status)
    }

    private fun thenSessionIsCrashed(scope: IScope) {
        val sessionAfterCapture = scope.withSession { }!!
        assertTrue(sessionAfterCapture.errorCount() > 0)
        assertEquals(Session.State.Crashed, sessionAfterCapture.status)
    }

    class CustomBeforeSendCallback : SentryOptions.BeforeSendCallback {
        override fun execute(event: SentryEvent, hint: Hint): SentryEvent? {
            hint.screenshot = null
            hint.viewHierarchy = null
            hint.threadDump = null
            return event
        }
    }

    private fun createReplayEvent(): SentryReplayEvent = SentryReplayEvent().apply {
        replayId = SentryId("f715e1d64ef64ea3ad7744b5230813c3")
        segmentId = 0
        timestamp = DateUtils.getDateTimeWithMillisPrecision("987654321.123")
        replayStartTimestamp = DateUtils.getDateTimeWithMillisPrecision("987654321.123")
        urls = listOf("ScreenOne")
        errorIds = listOf("ab3a347a4cc14fd4b4cf1dc56b670c5b")
        traceIds = listOf("340cfef948204549ac07c3b353c81c50")
    }

    private fun createReplayRecording(): ReplayRecording = ReplayRecording().apply {
        segmentId = 0
        payload = emptyList()
    }

    private fun createScope(options: SentryOptions = SentryOptions()): IScope {
        return Scope(options).apply {
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

    private fun createScopeWithAttachments(): IScope {
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

    private val checkIn = CheckIn("some_slug", CheckInStatus.OK)

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

    private fun createHandledException(): List<SentryException> {
        return listOf(SentryException())
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

    private fun getReplayFromData(data: ByteArray): SentryReplayEvent? {
        val unpacker = MessagePack.newDefaultUnpacker(data)
        val mapSize = unpacker.unpackMapHeader()
        for (i in 0 until mapSize) {
            val key = unpacker.unpackString()
            when (key) {
                SentryItemType.ReplayEvent.itemType -> {
                    val replayEventLength = unpacker.unpackBinaryHeader()
                    val replayEventBytes = unpacker.readPayload(replayEventLength)
                    return fixture.sentryOptions.serializer.deserialize(
                        InputStreamReader(replayEventBytes.inputStream()),
                        SentryReplayEvent::class.java
                    )!!
                }
            }
        }
        return null
    }

    private fun verifyAttachmentsInEnvelope(eventId: SentryId?) {
        verify(fixture.transport).send(
            check { actual ->
                assertEquals(eventId, actual.header.eventId)

                assertEquals(fixture.sentryOptions.sdkVersion, actual.header.sdkVersion)

                assertEquals(4, actual.items.filter { it.header.type != SentryItemType.Profile }.count())
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
            anyOrNull()
        )
    }

    private fun verifyProfilingTraceInEnvelope(eventId: SentryId?) {
        verify(fixture.transport).send(
            check { actual ->
                assertEquals(eventId, actual.header.eventId)

                val profilingTraceItem = actual.items.firstOrNull { item ->
                    item.header.type == SentryItemType.Profile
                }
                assertNotNull(profilingTraceItem?.data)
            },
            anyOrNull()
        )
    }

    private fun verifyProfileChunkInEnvelope(eventId: SentryId?) {
        verify(fixture.transport).send(
            check { actual ->
                assertEquals(eventId, actual.header.eventId)

                val profilingTraceItem = actual.items.firstOrNull { item ->
                    item.header.type == SentryItemType.ProfileChunk
                }
                assertNotNull(profilingTraceItem?.data)
            }
        )
    }

    private class AbnormalHint(private val mechanism: String? = null) : AbnormalExit {
        override fun mechanism(): String? = mechanism
        override fun ignoreCurrentThread(): Boolean = false
        override fun timestamp(): Long? = null
    }

    private fun eventProcessorThrows(): EventProcessor {
        return object : EventProcessor {
            override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
                throw Throwable()
            }
        }
    }

    private class BackfillableHint : Backfillable {
        override fun shouldEnrich(): Boolean = false
    }
}

class DropEverythingEventProcessor : EventProcessor {

    override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
        return null
    }

    override fun process(
        transaction: SentryTransaction,
        hint: Hint
    ): SentryTransaction? {
        return null
    }

    override fun process(event: SentryReplayEvent, hint: Hint): SentryReplayEvent? {
        return null
    }
}
