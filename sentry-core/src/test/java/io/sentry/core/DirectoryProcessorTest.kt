package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.hints.ApplyScopeData
import io.sentry.core.protocol.User
import io.sentry.core.util.noFlushTimeout
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
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): EnvelopeSender {
            return EnvelopeSender(hub, envelopeReader, serializer, logger, 15000)
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
        val session = createSession()
        whenever(fixture.envelopeReader.read(any())).thenReturn(SentryEnvelope.fromSession(fixture.serializer, session, null))
        whenever(fixture.serializer.deserializeSession(any())).thenReturn(session)
        fixture.getSut().processDirectory(file)
        verify(fixture.hub).captureEnvelope(any(), argWhere { it !is ApplyScopeData })
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
