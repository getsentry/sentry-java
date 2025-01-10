package io.sentry

import io.sentry.cache.EnvelopeCache
import io.sentry.hints.Retryable
import io.sentry.util.HintUtils
import io.sentry.util.noFlushTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

class EnvelopeSenderTest {
    private class Fixture {
        var scopes: IScopes? = mock()
        var logger: ILogger? = mock()
        var serializer: ISerializer? = mock()
        var options = SentryOptions().noFlushTimeout()

        init {
            options.setDebug(true)
            options.setLogger(logger)
        }

        fun getSut(): EnvelopeSender {
            return EnvelopeSender(
                scopes!!,
                serializer!!,
                logger!!,
                options.flushTimeoutMillis,
                options.maxQueueSize
            )
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
        verifyNoMoreInteractions(fixture.scopes)
    }

    @Test
    fun `when directory is actually a file, processDirectory logs and returns`() {
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile("send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()
        sut.processDirectory(testFile)
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq("Cache dir %s is not a directory."), any<Any>())
        verifyNoMoreInteractions(fixture.scopes)
    }

    @Test
    fun `when directory has non event files, processDirectory logs that`() {
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", ".not-right-suffix").toUri())
        sut.processDirectory(File(tempDirectory.toUri()))
        testFile.deleteOnExit()
        verify(fixture.logger)!!.log(eq(SentryLevel.DEBUG), eq("File '%s' doesn't match extension expected."), any<Any>())
        verify(fixture.scopes, never())!!.captureEnvelope(any(), anyOrNull())
    }

    @Test
    fun `when directory has event files, processDirectory captures with scopes`() {
        val event = SentryEvent()
        val envelope = SentryEnvelope.from(fixture.serializer!!, event, null)
        whenever(fixture.serializer!!.deserializeEnvelope(any())).thenReturn(envelope)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()
        sut.processDirectory(File(tempDirectory.toUri()))
        verify(fixture.scopes)!!.captureEnvelope(eq(envelope), any())
    }

    @Test
    fun `when serializer throws, error is logged, file deleted`() {
        val expected = RuntimeException()
        whenever(fixture.serializer!!.deserializeEnvelope(any())).doThrow(expected)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()

        val hints = HintUtils.createWithTypeCheckHint(mock<Retryable>())
        sut.processFile(testFile, hints)
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq(expected), eq("Failed to capture cached envelope %s"), eq(testFile.absolutePath))
        verifyNoMoreInteractions(fixture.scopes)
        assertFalse(testFile.exists())
    }

    @Test
    fun `when scopes throws, file gets deleted`() {
        val expected = RuntimeException()
        whenever(fixture.serializer!!.deserializeEnvelope(any())).doThrow(expected)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", EnvelopeCache.SUFFIX_ENVELOPE_FILE).toUri())
        testFile.deleteOnExit()
        sut.processFile(testFile, Hint())
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq(expected), eq("Failed to capture cached envelope %s"), eq(testFile.absolutePath))
        verifyNoMoreInteractions(fixture.scopes)
    }
}
