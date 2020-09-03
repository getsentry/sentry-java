package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.cache.EnvelopeCache
import io.sentry.core.hints.Retryable
import io.sentry.core.protocol.SentryId
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutboxSenderTest {
    private class Fixture {

        var hub: IHub = mock()
        var envelopeReader: IEnvelopeReader = mock()
        var serializer: ISerializer = mock()
        var logger: ILogger = mock()
        var options: SentryOptions

        init {
            options = SentryOptions()
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): OutboxSender {
            return OutboxSender(hub, envelopeReader, serializer, logger, 15000)
        }
    }

    private val fixture = Fixture()

    private fun getTempEnvelope(fileName: String): String {
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
        val path = getTempEnvelope("envelope-event-attachment.txt")
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path, mock<Retryable>())
        assertFalse(File(path).exists())
        // Additionally make sure we have a error logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns SentryEvent, event captured, file is deleted `() {
        fixture.envelopeReader = EnvelopeReader()
        val expected = SentryEvent(SentryId(UUID.fromString("9ec79c33-ec99-42ab-8353-589fcb2e04dc")), Date())
        whenever(fixture.serializer.deserializeEvent(any())).thenReturn(expected)
        val sut = fixture.getSut()
        val path = getTempEnvelope("envelope-event-attachment.txt")
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path, mock<Retryable>())

        verify(fixture.hub).captureEvent(eq(expected), any())
        assertFalse(File(path).exists())
        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns SentryEnvelope, event captured, file is deleted `() {
        fixture.envelopeReader = EnvelopeReader()

        val event = SentryEvent(SentryId("9ec79c33ec9942ab8353589fcb2e04dc"), Date())
        val expected = SentryEnvelope(SentryId("9ec79c33ec9942ab8353589fcb2e04dc"), null, setOf())
        whenever(fixture.serializer.deserializeEnvelope(any())).thenReturn(expected)
        whenever(fixture.serializer.deserializeEvent(any())).thenReturn(event)
        val sut = fixture.getSut()
        val path = getTempEnvelope("envelope-event-attachment.txt")
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path, mock<Retryable>())

        verify(fixture.hub).captureEvent(any(), any())
        assertFalse(File(path).exists())
        // Additionally make sure we have no errors logged
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns a null event, file error logged, no event captured `() {
        fixture.envelopeReader = EnvelopeReader()
        whenever(fixture.serializer.deserializeEvent(any())).thenReturn(null)
        val sut = fixture.getSut()
        val path = getTempEnvelope("envelope-event-attachment.txt")
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path, mock<Retryable>())

        // Additionally make sure we have no errors logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.hub, never()).captureEvent(any())
        assertFalse(File(path).exists())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns a null envelope, file error logged, no event captured `() {
        fixture.envelopeReader = EnvelopeReader()
        whenever(fixture.serializer.deserializeEnvelope(any())).thenReturn(null)
        whenever(fixture.serializer.deserializeEvent(any())).thenReturn(null)
        val sut = fixture.getSut()
        val path = getTempEnvelope("envelope-event-attachment.txt")
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path, mock<Retryable>())

        // Additionally make sure we have no errors logged
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.hub, never()).captureEvent(any())
        assertFalse(File(path).exists())
    }

    @Test
    fun `when processEnvelopeFile is called with a invalid path, logs error`() {
        val sut = fixture.getSut()
        sut.processEnvelopeFile(File.separator + "i-hope-it-doesnt-exist" + File.separator + "file.txt", mock<Retryable>())
        verify(fixture.logger).log(eq(SentryLevel.ERROR), any<String>(), argWhere { it is FileNotFoundException })
    }

    @Test
    fun `when hub is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.core.OutboxSender")
        val ctor = clazz.getConstructor(IHub::class.java, IEnvelopeReader::class.java, ISerializer::class.java, ILogger::class.java, Long::class.java)
        val params = arrayOf(null, mock<IEnvelopeReader>(), mock<ISerializer>(), mock<ILogger>(), null)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when envelopeReader is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.core.OutboxSender")
        val ctor = clazz.getConstructor(IHub::class.java, IEnvelopeReader::class.java, ISerializer::class.java, ILogger::class.java, Long::class.java)
        val params = arrayOf(mock<IHub>(), null, mock<ISerializer>(), mock<ILogger>(), 15000)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when serializer is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.core.OutboxSender")
        val ctor = clazz.getConstructor(IHub::class.java, IEnvelopeReader::class.java, ISerializer::class.java, ILogger::class.java, Long::class.java)
        val params = arrayOf(mock<IHub>(), mock<IEnvelopeReader>(), null, mock<ILogger>(), 15000)
        assertFailsWith<IllegalArgumentException> { ctor.newInstance(params) }
    }

    @Test
    fun `when logger is null, ctor throws`() {
        val clazz = Class.forName("io.sentry.core.OutboxSender")
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
