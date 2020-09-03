package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.cache.EnvelopeCache
import io.sentry.core.hints.Retryable
import io.sentry.core.util.NoFlushTimeout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

class EnvelopeSenderTest {
    private class Fixture {
        var hub: IHub? = mock()
        var logger: ILogger? = mock()
        var serializer: ISerializer? = mock()
        var options = SentryOptions().NoFlushTimeout()

        init {
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): EnvelopeSender {
            return EnvelopeSender(hub!!, serializer!!, logger!!, options.flushTimeoutMillis)
        }
    }

    private lateinit var tempDirectory: Path
    private val fixture = Fixture()

    @BeforeTest
    fun `before send`() {
        tempDirectory = Files.createTempDirectory("send-cached-event-test")
    }

    @AfterTest
    fun `after send`() {
        File(tempDirectory.toUri()).delete()
    }

    @Test
    fun `when directory doesn't exist, processDirectory logs and returns`() {
        val sut = fixture.getSut()
        sut.processDirectory(File("i don't exist"))
        verify(fixture.logger)!!.log(eq(SentryLevel.WARNING), eq("Directory '%s' doesn't exist. No cached events to send."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `when directory is actually a file, processDirectory logs and returns`() {
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile("send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()
        sut.processDirectory(testFile)
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq("Cache dir %s is not a directory."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `when directory has non event files, processDirectory logs that`() {
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", ".not-right-suffix").toUri())
        sut.processDirectory(File(tempDirectory.toUri()))
        testFile.deleteOnExit()
        verify(fixture.logger)!!.log(eq(SentryLevel.DEBUG), eq("File '%s' doesn't match extension expected."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `when directory has event files, processDirectory captures with hub`() {
        val event = SentryEvent()
        val envelope = SentryEnvelope.fromEvent(fixture.serializer!!, event, null)
        whenever(fixture.serializer!!.deserializeEnvelope(any())).thenReturn(envelope)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()
        sut.processDirectory(File(tempDirectory.toUri()))
        verify(fixture.hub)!!.captureEnvelope(eq(envelope), any())
    }

    @Test
    fun `when serializer throws, error is logged, file deleted`() {
        val expected = RuntimeException()
        whenever(fixture.serializer!!.deserializeEnvelope(any())).doThrow(expected)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()
        sut.processFile(testFile, mock<Retryable>())
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq(expected), eq("Failed to capture cached envelope %s"), eq(testFile.absolutePath))
        verifyNoMoreInteractions(fixture.hub)
        assertFalse(testFile.exists())
    }

    @Test
    fun `when hub throws, file gets deleted`() {
        val expected = RuntimeException()
        whenever(fixture.serializer!!.deserializeEnvelope(any())).doThrow(expected)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()
        sut.processFile(testFile, any())
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq(expected), eq("Failed to capture cached envelope %s"), eq(testFile.absolutePath))
        verifyNoMoreInteractions(fixture.hub)
    }
}
