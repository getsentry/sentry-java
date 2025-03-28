package io.sentry.instrumentation.file

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.protocol.SentryStackFrame
import io.sentry.util.thread.ThreadChecker
import org.awaitility.kotlin.await
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryFileOutputStreamTest {
    class Fixture {
        val scopes = mock<IScopes>()
        val options = SentryOptions()

        lateinit var sentryTracer: SentryTracer

        internal fun getSut(
            tmpFile: File? = null,
            activeTransaction: Boolean = true,
            append: Boolean = false,
            optionsConfiguration: (SentryOptions) -> Unit = {}
        ): SentryFileOutputStream {
            val options = SentryOptions().apply {
                threadChecker = ThreadChecker.getInstance()
                addInAppInclude("org.junit")
                optionsConfiguration(this)
            }
            whenever(scopes.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
            if (activeTransaction) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }
            whenever(scopes.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
            if (activeTransaction) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }
            return SentryFileOutputStream(tmpFile, append, scopes)
        }

        internal fun getSut(
            tmpFile: File? = null,
            delegate: FileOutputStream,
            tracesSampleRate: Double? = 1.0
        ): FileOutputStream {
            options.tracesSampleRate = tracesSampleRate
            whenever(scopes.options).thenReturn(options)
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
            whenever(scopes.span).thenReturn(sentryTracer)
            return SentryFileOutputStream.Factory.create(
                delegate,
                tmpFile,
                scopes
            )
        }
    }

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val fixture = Fixture()

    private val tmpFile: File get() = tmpDir.newFile("test.txt")

    private val tmpFileWithoutExtension: File get() = tmpDir.newFile("test")

    @Test
    fun `when no active transaction does not capture a span`() {
        fixture.getSut(tmpFile, activeTransaction = false)
            .use { it.write("Text".toByteArray()) }

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
        val fos = fixture.getSut(tmpFile)
        fos.close()

        assertEquals(fixture.sentryTracer.children.size, 1)
        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "***.txt (0 B)")
        assertEquals(fileIOSpan.data["file.size"], 0L)
        assertEquals(fileIOSpan.throwable, null)
        assertEquals(fileIOSpan.isFinished, true)
        assertEquals(fileIOSpan.status, SpanStatus.OK)
    }

    @Test
    fun `captures file name in description and file path when isSendDefaultPii is true`() {
        val fos = fixture.getSut(tmpFile) {
            it.isSendDefaultPii = true
        }
        fos.close()

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (0 B)")
        assertNotNull(fileIOSpan.data["file.path"])
    }

    @Test
    fun `captures only file extension in description when isSendDefaultPii is false`() {
        val fos = fixture.getSut(tmpFile) {
            it.isSendDefaultPii = false
        }
        fos.close()

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "***.txt (0 B)")
        assertNull(fileIOSpan.data["file.path"])
    }

    @Test
    fun `captures only file size if no extension is available when isSendDefaultPii is false`() {
        val fos = fixture.getSut(tmpFileWithoutExtension) {
            it.isSendDefaultPii = false
        }
        fos.close()

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "*** (0 B)")
        assertNull(fileIOSpan.data["file.path"])
    }

    @Test
    fun `when stream is closed file descriptor is also closed`() {
        val fos = fixture.getSut(tmpFile)
        fos.use { it.write("hello".toByteArray()) }
        assertFalse(fos.fd.valid())
    }

    @Test
    fun `write one byte`() {
        fixture.getSut(tmpFile).use { it.write(29) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "***.txt (1 B)")
        assertEquals(fileIOSpan.data["file.size"], 1L)
    }

    @Test
    fun `write array of bytes`() {
        fixture.getSut(tmpFile).use { it.write(ByteArray(10)) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "***.txt (10 B)")
        assertEquals(fileIOSpan.data["file.size"], 10L)
    }

    @Test
    fun `write array range of bytes`() {
        fixture.getSut(tmpFile).use { it.write(ByteArray(10), 1, 3) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "***.txt (3 B)")
        assertEquals(fileIOSpan.data["file.size"], 3L)
    }

    @Test
    fun `write all bytes`() {
        fixture.getSut(tmpFile).use { it.write("Text".toByteArray()) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
        assertEquals(fileIOSpan.data["file.size"], 4L)
    }

    @Test
    fun `when run on main thread, attaches call_stack with blocked_main_thread=true`() {
        val fis = fixture.getSut(tmpFile)
        fis.close()

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(true, fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY])
        // assuming our "in-app" is org.junit, we check that only org.junit frames are in the call stack
        assertTrue {
            (fileIOSpan.data[SpanDataConvention.CALL_STACK_KEY] as List<SentryStackFrame>).all {
                it.module?.startsWith("org.junit") == true
            }
        }
    }

    @Test
    fun `when run on a background thread, does not attach call_stack with blocked_main_thread=false`() {
        val finished = AtomicBoolean(false)
        thread {
            val fis = fixture.getSut(tmpFile)
            fis.close()
            finished.set(true)
        }

        await.untilTrue(finished)

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(false, fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY])
        assertNull(fileIOSpan.data[SpanDataConvention.CALL_STACK_KEY])
    }

    @Test
    fun `when tracing is disabled does not instrument the stream`() {
        val file = tmpFile
        val delegate = ThrowingFileOutputStream(file)
        val stream = fixture.getSut(file, delegate = delegate, tracesSampleRate = null)

        assertTrue { stream is ThrowingFileOutputStream }
    }
}

class ThrowingFileOutputStream(file: File) : FileOutputStream(file) {
    val throwable = IOException("Oops!")

    override fun write(b: Int) {
        throw throwable
    }

    override fun close() {
        throw throwable
    }
}
