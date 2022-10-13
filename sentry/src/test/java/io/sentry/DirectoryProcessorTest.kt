package io.sentry

import io.sentry.hints.ApplyScopeData
import io.sentry.protocol.User
import io.sentry.util.HintUtils
import io.sentry.util.noFlushTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DirectoryProcessorTest {

    private class Fixture {

        var hub: IHub = mock()
        var envelopeReader: IEnvelopeReader = mock()
        var serializer: ISerializer = mock()
        var logger: ILogger = mock()
        var options = SentryOptions().noFlushTimeout()

        init {
            options.setDebug(true)
            options.setLogger(logger)
        }

        fun getSut(): OutboxSender {
            return OutboxSender(hub, envelopeReader, serializer, logger, 15000)
        }
    }

    private val fixture = Fixture()

    private lateinit var file: File

    @BeforeTest
    fun `set up`() {
        file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    }

    @AfterTest
    fun shutdown() {
        file.deleteRecursively()
    }

    @Test
    fun `process directory folder has a non ApplyScopeData hint`() {
        val path = getTempEnvelope("envelope-event-attachment.txt")
        assertTrue(File(path).exists()) // sanity check
//        val session = createSession()
//        whenever(fixture.envelopeReader.read(any())).thenReturn(SentryEnvelope.from(fixture.serializer, session, null))
//        whenever(fixture.serializer.deserializeSession(any())).thenReturn(session)
        val event = SentryEvent()
        val envelope = SentryEnvelope.from(fixture.serializer, event, null)

        whenever(fixture.envelopeReader.read(any())).thenReturn(envelope)
        whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)

        fixture.getSut().processDirectory(file)
        verify(fixture.hub).captureEvent(any(), argWhere<Hint> { !HintUtils.hasType(it, ApplyScopeData::class.java) })
    }

    @Test
    fun `process directory ignores non files on the cache folder`() {
        val dir = File(file.absolutePath, "testDir")
        dir.mkdirs()
        assertTrue(dir.exists()) // sanity check
        fixture.getSut().processDirectory(file)
        verify(fixture.hub, never()).captureEnvelope(any(), any())
    }

    private fun getTempEnvelope(fileName: String): String {
        val testFile = this::class.java.classLoader.getResource(fileName)
        val testFileBytes = testFile!!.readBytes()
        val targetFile = File.createTempFile("temp-envelope", ".tmp", file)
        Files.write(Paths.get(targetFile.toURI()), testFileBytes)
        return targetFile.absolutePath
    }

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }
}
