package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.cache.DiskCache
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

class SendCachedEventTest {
    private class Fixture {
        var hub: IHub? = mock()
        var logger: ILogger? = mock()
        var serializer: ISerializer? = mock()
        var options = SentryOptions()

        init {
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): SendCachedEvent {
            return SendCachedEvent(serializer!!, hub!!, logger!!)
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
    fun `when directory doesn't exist, sendCachedFiles logs and returns`() {
        val sut = fixture.getSut()
        sut.sendCachedFiles(File("i don't exist"))
        verify(fixture.logger)!!.log(eq(SentryLevel.WARNING), eq("Directory '%s' doesn't exist. No cached events to send."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `when directory is actually a file, sendCachedFiles logs and returns`() {
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile("send-cached-event-test", "").toUri())
        testFile.deleteOnExit()
        sut.sendCachedFiles(testFile)
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq("Cache dir %s is not a directory."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `when directory has non event files, sendCachedFiles logs that`() {
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", ".not-right-suffix").toUri())
        testFile.deleteOnExit()
        sut.sendCachedFiles(File(tempDirectory.toUri()))
        verify(fixture.logger)!!.log(eq(SentryLevel.DEBUG), eq("File '%s' doesn't match extension expected."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `when directory has event files, sendCachedFiles captures with hub`() {
        val expected = SentryEvent()
        whenever(fixture.serializer!!.deserializeEvent(any())).thenReturn(expected)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", DiskCache.FILE_SUFFIX).toUri())
        testFile.deleteOnExit()
        sut.sendCachedFiles(File(tempDirectory.toUri()))
        verify(fixture.hub)!!.captureEvent(eq(expected), any())
    }

    @Test
    fun `when serializer throws, error is logged, file deleted`() {
        val expected = RuntimeException()
        whenever(fixture.serializer!!.deserializeEvent(any())).doThrow(expected)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", DiskCache.FILE_SUFFIX).toUri())
        testFile.deleteOnExit()
        sut.sendCachedFiles(File(tempDirectory.toUri()))
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq("Failed to capture cached event."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
        assertFalse(testFile.exists())
    }

    @Test
    fun `when hub throws, file gets deleted`() {
        whenever(fixture.serializer!!.deserializeEvent(any())).thenReturn(SentryEvent())
        val expected = RuntimeException()
        whenever(fixture.serializer!!.deserializeEvent(any())).doThrow(expected)
        val sut = fixture.getSut()
        val testFile = File(Files.createTempFile(tempDirectory, "send-cached-event-test", DiskCache.FILE_SUFFIX).toUri())
        testFile.deleteOnExit()
        sut.sendCachedFiles(File(tempDirectory.toUri()))
        verify(fixture.logger)!!.log(eq(SentryLevel.ERROR), eq("Failed to capture cached event."), any<Any>())
        verifyNoMoreInteractions(fixture.hub)
    }
}
