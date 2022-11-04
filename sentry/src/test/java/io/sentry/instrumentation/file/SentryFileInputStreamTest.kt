package io.sentry.instrumentation.file

import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.SpanStatus.INTERNAL_ERROR
import io.sentry.TransactionContext
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SentryFileInputStreamTest {

    class Fixture {
        val hub = mock<IHub>()
        lateinit var sentryTracer: SentryTracer
        private val options = SentryOptions()

        internal fun getSut(
            tmpFile: File? = null,
            activeTransaction: Boolean = true,
            fileDescriptor: FileDescriptor? = null,
            sendDefaultPii: Boolean = false
        ): SentryFileInputStream {
            tmpFile?.writeText("Text")
            whenever(hub.options).thenReturn(
                options.apply { isSendDefaultPii = sendDefaultPii }
            )
            sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)
            if (activeTransaction) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            return if (fileDescriptor == null) {
                SentryFileInputStream(tmpFile, hub)
            } else {
                SentryFileInputStream(fileDescriptor, hub)
            }
        }

        internal fun getSut(
            tmpFile: File? = null,
            delegate: FileInputStream
        ): SentryFileInputStream {
            whenever(hub.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)
            whenever(hub.span).thenReturn(sentryTracer)
            return SentryFileInputStream.Factory.create(
                delegate,
                tmpFile,
                hub
            ) as SentryFileInputStream
        }
    }

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val fixture = Fixture()

    private val tmpFile: File get() = tmpDir.newFile("test.txt")

    @Test
    fun `when no active transaction does not capture a span`() {
        fixture.getSut(tmpFile, activeTransaction = false)
            .use { it.readAllBytes() }

        assertEquals(fixture.sentryTracer.children.size, 0)
    }

    @Test
    fun `when stream is not closed does not finish a span`() {
        fixture.getSut(tmpFile)

        assertEquals(fixture.sentryTracer.children.size, 1)
        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.isFinished, false)
    }

    @Test
    fun `when stream is closed captures a span`() {
        val fis = fixture.getSut(tmpFile)
        fis.close()

        assertEquals(fixture.sentryTracer.children.size, 1)
        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (0 B)")
        assertEquals(fileIOSpan.data["file.size"], 0L)
        assertEquals(fileIOSpan.throwable, null)
        assertEquals(fileIOSpan.isFinished, true)
        assertEquals(fileIOSpan.status, SpanStatus.OK)
    }

    @Test
    fun `when stream is closed, releases file descriptor`() {
        val fis = fixture.getSut(tmpFile)
        fis.use { it.readAllBytes() }
        assertFalse(fis.fd.valid())
    }

    @Test
    fun `read one byte`() {
        fixture.getSut(tmpFile).use { it.read() }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (1 B)")
        assertEquals(fileIOSpan.data["file.size"], 1L)
    }

    @Test
    fun `read array of bytes`() {
        fixture.getSut(tmpFile).use { it.read(ByteArray(10)) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
        assertEquals(fileIOSpan.data["file.size"], 4L)
    }

    @Test
    fun `read array range of bytes`() {
        fixture.getSut(tmpFile).use { it.read(ByteArray(10), 1, 3) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (3 B)")
        assertEquals(fileIOSpan.data["file.size"], 3L)
    }

    @Test
    fun `skip bytes`() {
        fixture.getSut(tmpFile).use { it.skip(10) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (10 B)")
        assertEquals(fileIOSpan.data["file.size"], 10L)
    }

    @Test
    fun `read all bytes`() {
        fixture.getSut(tmpFile).use { it.reader().readText() }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
        assertEquals(fileIOSpan.data["file.size"], 4L)
    }

    @Test
    fun `when IO operation throws captures Throwable and sets status to INTERNAL_ERROR`() {
        val file = tmpFile
        val delegate = ThrowingFileInputStream(file)
        try {
            fixture.getSut(file, delegate = delegate).use { it.read() }
        } catch (e: IOException) {
            // ignored
        }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.status, INTERNAL_ERROR)
        assertEquals(fileIOSpan.throwable, delegate.throwable)
    }

    @Test
    fun `when close() throws captures Throwable and sets status to INTERNAL_ERROR`() {
        val file = tmpFile
        val delegate = ThrowingFileInputStream(file)
        try {
            val fis = fixture.getSut(file, delegate = delegate)
            fis.close()
        } catch (e: IOException) {
            // ignored
        }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.status, INTERNAL_ERROR)
        assertEquals(fileIOSpan.throwable, delegate.throwable)
    }

    @Test
    fun `when instantiated with file descriptor only reports file size as description`() {
        val file = tmpFile.apply { writeText("Text") }
        val fd = FileInputStream(file).fd
        fixture.getSut(fileDescriptor = fd).use { it.read() }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.description, "1 B")
    }

    @Test
    fun `when run on JVM and sendDefaultPii is enabled reports file path`() {
        val file = tmpFile
        fixture.getSut(file, sendDefaultPii = true).use { it.readAllBytes() }
        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.data["file.path"], file.absolutePath)
    }
}

class ThrowingFileInputStream(file: File) : FileInputStream(file) {
    val throwable = IOException("Oops!")

    override fun read(): Int {
        throw throwable
    }

    override fun close() {
        throw throwable
    }
}
