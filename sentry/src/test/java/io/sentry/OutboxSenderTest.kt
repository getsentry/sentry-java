package io.sentry

import io.sentry.cache.EnvelopeCache
import io.sentry.hints.Retryable
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.util.HintUtils
import io.sentry.util.thread.NoOpThreadChecker
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutboxSenderTest {
    private class Fixture {

        val options = mock<SentryOptions>()
        val scopes = mock<IScopes>()
        var envelopeReader = mock<IEnvelopeReader>()
        val serializer = mock<ISerializer>()
        val logger = mock<ILogger>()

        init {
            whenever(options.dsn).thenReturn("https://key@sentry.io/proj")
            whenever(options.dateProvider).thenReturn(SentryNanotimeDateProvider())
            whenever(options.threadChecker).thenReturn(NoOpThreadChecker.getInstance())
            whenever(scopes.options).thenReturn(this.options)
        }

        fun getSut(): OutboxSender {
            return OutboxSender(scopes, envelopeReader, serializer, logger, 15000, 30)
        }
    }

    private val fixture = Fixture()

    private fun getTempEnvelope(fileName: String = "envelope-event-attachment.txt"): String {
        val testFile = this::class.java.classLoader.getResource(fileName)
        val testFileBytes = testFile!!.readBytes()
        val targetFile = File.createTempFile("temp-envelope", ".tmp")
        Files.write(Paths.get(targetFile.toURI()), testFileBytes)
        return targetFile.absolutePath
    }

    @Test
    fun `when envelopeReader returns null, file is deleted `() {
        whenever(fixture.envelopeReader.read(any())).thenReturn(null)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)
        assertFalse(File(path).exists())
        // Additionally make sure we have a error logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns SentryEvent, event captured, file is deleted `() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
        val expected = SentryEvent(SentryId("9ec79c33-ec99-42ab-8353-589fcb2e04dc"), Date())
        whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(expected)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)

        verify(fixture.scopes).captureEvent(eq(expected), any<Hint>())
        assertFalse(File(path).exists())
        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer return SentryTransaction, transaction captured, transactions sampled, file is deleted`() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
        whenever(fixture.options.maxSpans).thenReturn(1000)
        whenever(fixture.scopes.options).thenReturn(fixture.options)
        whenever(fixture.options.transactionProfiler).thenReturn(NoOpTransactionProfiler.getInstance())

        val transactionContext = TransactionContext("fixture-name", "http")
        transactionContext.description = "fixture-request"
        transactionContext.status = SpanStatus.OK
        transactionContext.setTag("fixture-tag", "fixture-value")

        val sentryTracer = SentryTracer(transactionContext, fixture.scopes)
        val span = sentryTracer.startChild("child")
        span.finish(SpanStatus.OK)
        sentryTracer.finish()

        val sentryTracerSpy = spy(sentryTracer)
        whenever(sentryTracerSpy.eventId).thenReturn(SentryId("3367f5196c494acaae85bbbd535379ac"))

        val expected = SentryTransaction(sentryTracerSpy)
        whenever(fixture.serializer.deserialize(any(), eq(SentryTransaction::class.java))).thenReturn(expected)

        val sut = fixture.getSut()
        val path = getTempEnvelope(fileName = "envelope-transaction.txt")
        assertTrue(File(path).exists())

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)

        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(expected, it)
                assertTrue(it.isSampled)
            },
            any(),
            any()
        )
        assertFalse(File(path).exists())

        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `restores sampleRate`() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
        whenever(fixture.options.maxSpans).thenReturn(1000)
        whenever(fixture.scopes.options).thenReturn(fixture.options)
        whenever(fixture.options.transactionProfiler).thenReturn(NoOpTransactionProfiler.getInstance())

        val transactionContext = TransactionContext("fixture-name", "http")
        transactionContext.description = "fixture-request"
        transactionContext.status = SpanStatus.OK
        transactionContext.setTag("fixture-tag", "fixture-value")
        transactionContext.samplingDecision = TracesSamplingDecision(true, 0.00000021)

        val sentryTracer = SentryTracer(transactionContext, fixture.scopes)
        val span = sentryTracer.startChild("child")
        span.finish(SpanStatus.OK)
        sentryTracer.finish()

        val sentryTracerSpy = spy(sentryTracer)
        whenever(sentryTracerSpy.eventId).thenReturn(SentryId("3367f5196c494acaae85bbbd535379ac"))

        val expected = SentryTransaction(sentryTracerSpy)
        whenever(fixture.serializer.deserialize(any(), eq(SentryTransaction::class.java))).thenReturn(expected)

        val sut = fixture.getSut()
        val path = getTempEnvelope(fileName = "envelope-transaction-with-sample-rate.txt")
        assertTrue(File(path).exists())

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)

        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(expected, it)
                assertTrue(it.isSampled)
                assertEquals(0.00000021, it.samplingDecision?.sampleRate)
                assertTrue(it.samplingDecision!!.sampled)
            },
            check {
                assertEquals("b156a475de54423d9c1571df97ec7eb6", it.traceId.toString())
                assertEquals("key", it.publicKey)
                assertEquals("0.00000021", it.sampleRate)
                assertEquals("1.0-beta.1", it.release)
                assertEquals("prod", it.environment)
                assertEquals("usr1", it.userId)
                assertEquals("tx1", it.transaction)
            },
            any()
        )
        assertFalse(File(path).exists())

        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns SentryEnvelope, event captured, file is deleted `() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))

        val event = SentryEvent(SentryId("9ec79c33ec9942ab8353589fcb2e04dc"), Date())
        val expected = SentryEnvelope(SentryId("9ec79c33ec9942ab8353589fcb2e04dc"), null, setOf())
        whenever(fixture.serializer.deserializeEnvelope(any())).thenReturn(expected)
        whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)

        verify(fixture.scopes).captureEvent(any(), any<Hint>())
        assertFalse(File(path).exists())
        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `when envelope has unknown item type, create and capture an envelope`() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))

        val sut = fixture.getSut()
        val path = getTempEnvelope(fileName = "envelope_attachment.txt")
        assertTrue(File(path).exists()) // sanity check

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)

        verify(fixture.scopes).captureEnvelope(any(), any())
        assertFalse(File(path).exists())
        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns a null event, file error logged, no event captured `() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
        whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(null)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)

        // Additionally make sure we have no errors logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.scopes, never()).captureEvent(any())
        assertFalse(File(path).exists())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns a null envelope, file error logged, no event captured `() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
        whenever(fixture.serializer.deserializeEnvelope(any())).thenReturn(null)
        whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(null)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(path, hints)

        // Additionally make sure we have no errors logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.scopes, never()).captureEvent(any())
        assertFalse(File(path).exists())
    }

    @Test
    fun `when processEnvelopeFile is called with a invalid path, logs error`() {
        val sut = fixture.getSut()

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processEnvelopeFile(File.separator + "i-hope-it-doesnt-exist" + File.separator + "file.txt", hints)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any<String>(), argWhere { it is FileNotFoundException })
    }

    @Test
    fun `when file name is null, should not be relevant`() {
        assertFalse(fixture.getSut().isRelevantFileName(null))
    }

    @Test
    fun `when file name is current prefix, should be ignored`() {
        assertFalse(fixture.getSut().isRelevantFileName(EnvelopeCache.PREFIX_CURRENT_SESSION_FILE))
    }

    @Test
    fun `when file name is startup crash marker, should be ignored`() {
        assertFalse(fixture.getSut().isRelevantFileName(EnvelopeCache.STARTUP_CRASH_MARKER_FILE))
    }

    @Test
    fun `when file name is previous session file, should be ignored`() {
        assertFalse(fixture.getSut().isRelevantFileName(EnvelopeCache.PREFIX_PREVIOUS_SESSION_FILE))
    }

    @Test
    fun `when file name is relevant, should return true`() {
        assertTrue(fixture.getSut().isRelevantFileName("123.envelope"))
    }
}
