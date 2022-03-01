package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.cache.EnvelopeCache
import io.sentry.hints.Retryable
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutboxSenderTest {
    private class Fixture {

        val options = mock<SentryOptions>()
        val hub = mock<IHub>()
        var envelopeReader = mock<IEnvelopeReader>()
        val serializer = mock<ISerializer>()
        val logger = mock<ILogger>()

        init {
            whenever(options.dsn).thenReturn("https://key@sentry.io/proj")
            whenever(hub.options).thenReturn(this.options)
        }

        fun getSut(): OutboxSender {
            return OutboxSender(hub, envelopeReader, serializer, logger, 15000)
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

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(path, hintsMap)
        assertFalse(File(path).exists())
        // Additionally make sure we have a error logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns SentryEvent, event captured, file is deleted `() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
        val expected = SentryEvent(SentryId(UUID.fromString("9ec79c33-ec99-42ab-8353-589fcb2e04dc")), Date())
        whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(expected)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(path, hintsMap)

        verify(fixture.hub).captureEvent(eq(expected), any())
        assertFalse(File(path).exists())
        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer return SentryTransaction, transaction captured, transactions sampled, file is deleted`() {
        fixture.envelopeReader = EnvelopeReader(JsonSerializer(fixture.options))
        whenever(fixture.options.maxSpans).thenReturn(1000)
        whenever(fixture.hub.options).thenReturn(fixture.options)

        val transactionContext = TransactionContext("fixture-name", "http")
        transactionContext.description = "fixture-request"
        transactionContext.status = SpanStatus.OK
        transactionContext.setTag("fixture-tag", "fixture-value")

        val sentryTracer = SentryTracer(transactionContext, fixture.hub)
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

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(path, hintsMap)

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(expected, it)
                assertTrue(it.isSampled)
            },
            any(), any()
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

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(path, hintsMap)

        verify(fixture.hub).captureEvent(any(), any())
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

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(path, hintsMap)

        verify(fixture.hub).captureEnvelope(any(), any())
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

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(path, hintsMap)

        // Additionally make sure we have no errors logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.hub, never()).captureEvent(any())
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

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(path, hintsMap)

        // Additionally make sure we have no errors logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.hub, never()).captureEvent(any())
        assertFalse(File(path).exists())
    }

    @Test
    fun `when processEnvelopeFile is called with a invalid path, logs error`() {
        val sut = fixture.getSut()

        val hintsMap = mutableMapOf<String, Any>("sentrySdkHint" to mock<Retryable>())
        sut.processEnvelopeFile(File.separator + "i-hope-it-doesnt-exist" + File.separator + "file.txt", hintsMap)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any<String>(), argWhere { it is FileNotFoundException })
    }

    @Test
    fun `when hub is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.OutboxSender")
        val ctor = clazz.getConstructor(IHub::class.java, IEnvelopeReader::class.java, ISerializer::class.java, ILogger::class.java, Long::class.java)
        val params = arrayOf(null, mock<IEnvelopeReader>(), mock<ISerializer>(), mock<ILogger>(), null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when envelopeReader is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.OutboxSender")
        val ctor = clazz.getConstructor(IHub::class.java, IEnvelopeReader::class.java, ISerializer::class.java, ILogger::class.java, Long::class.java)
        val params = arrayOf(mock<IHub>(), null, mock<ISerializer>(), mock<ILogger>(), 15000)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when serializer is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.OutboxSender")
        val ctor = clazz.getConstructor(IHub::class.java, IEnvelopeReader::class.java, ISerializer::class.java, ILogger::class.java, Long::class.java)
        val params = arrayOf(mock<IHub>(), mock<IEnvelopeReader>(), null, mock<ILogger>(), 15000)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when logger is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.OutboxSender")
        val ctor = clazz.getConstructor(IHub::class.java, IEnvelopeReader::class.java, ISerializer::class.java, ILogger::class.java, Long::class.java)
        val params = arrayOf(mock<IHub>(), mock<IEnvelopeReader>(), mock<ISerializer>(), null, 15000)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
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
    fun `when file name is relevant, should return true`() {
        assertTrue(fixture.getSut().isRelevantFileName("123.envelope"))
    }
}
