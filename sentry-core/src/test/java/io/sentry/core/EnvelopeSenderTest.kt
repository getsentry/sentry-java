package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvelopeSenderTest {
    private class Fixture {

        var hub: IHub? = mock()
        var envelopeReader: IEnvelopeReader? = mock()
        var serializer: ISerializer? = mock()
        var logger: ILogger? = mock()

        init {
            val options = SentryOptions()
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): EnvelopeSender {
            return EnvelopeSender(hub, envelopeReader, serializer, logger)
        }
    }

    private val fixture = Fixture()

    private fun getTempEnvelope(): String {
        val testFile = this::class.java.classLoader.getResource("envelope-event-attachment.txt")
        val testFileBytes = testFile!!.readBytes()
        val targetFile = File.createTempFile("temp-envelope", ".tmp")
        Files.write(Paths.get(targetFile.toURI()), testFileBytes)
        return targetFile.absolutePath
    }

    @Test
    fun `when envelopeReader returns null, file is deleted `() {
        whenever(fixture.envelopeReader!!.read(any())).thenReturn(null)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path)
        assertFalse(File(path).exists())
        // Additionally make sure we have a error logged
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), any(), any<Any>())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns instance, event captured, file is deleted `() {
        fixture.envelopeReader = EnvelopeReader()
        val expected = SentryEvent()
        whenever(fixture.serializer!!.deserializeEvent(any<Reader>())).thenReturn(expected)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path)

        verify(fixture.hub, times(1))!!.captureEvent(expected)
        assertFalse(File(path).exists())
        // Additionally make sure we have no errors logged
        verify(fixture.logger, never())!!.log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.logger, never())!!.log(eq(SentryLevel.ERROR), any(), any())
    }

    @Test
    fun `when parser is EnvelopeReader and serializer returns null, file error logged, no event captured `() {
        fixture.envelopeReader = EnvelopeReader()
        whenever(fixture.serializer!!.deserializeEvent(any<Reader>())).thenReturn(null)
        val sut = fixture.getSut()
        val path = getTempEnvelope()
        assertTrue(File(path).exists()) // sanity check
        sut.processEnvelopeFile(path)

        // Additionally make sure we have no errors logged
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), any(), any<Any>())
        verify(fixture.hub, never())!!.captureEvent(any())
        assertFalse(File(path).exists())
    }

    @Test
    fun `when processEnvelopeFile is called with a invalid path, logs error`() {
        val sut = fixture.getSut()
        sut.processEnvelopeFile(File.separator + "i-hope-it-doesnt-exist" + File.separator + "file.txt")
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), any(), argWhere { it is FileNotFoundException })
    }

    @Test
    fun `when hub is null, ctor throws`() {
        fixture.hub = null
        assertFailsWith<IllegalArgumentException> { fixture.getSut() }
    }

    @Test
    fun `when envelopeReader is null, ctor throws`() {
        fixture.envelopeReader = null
        assertFailsWith<IllegalArgumentException> { fixture.getSut() }
    }

    @Test
    fun `when serializer is null, ctor throws`() {
        fixture.serializer = null
        assertFailsWith<IllegalArgumentException> { fixture.getSut() }
    }

    @Test
    fun `when logger is null, ctor throws`() {
        fixture.logger = null
        assertFailsWith<IllegalArgumentException> { fixture.getSut() }
    }
}
