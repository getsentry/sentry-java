package io.sentry.instrumentation.file

import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.util.thread.MainThreadChecker
import org.awaitility.kotlin.await
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryFileOutputStreamTest {
    class Fixture {
        val hub = mock<IHub>()
        lateinit var sentryTracer: SentryTracer

        internal fun getSut(
            tmpFile: File? = null,
            activeTransaction: Boolean = true,
            append: Boolean = false
        ): SentryFileOutputStream {
            whenever(hub.options).thenReturn(
                SentryOptions().apply {
                    mainThreadChecker = MainThreadChecker.getInstance()
                }
            )
            sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)
            if (activeTransaction) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            return SentryFileOutputStream(tmpFile, append, hub)
        }
    }

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val fixture = Fixture()

    private val tmpFile: File get() = tmpDir.newFile("test.txt")

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
        assertEquals(fileIOSpan.spanContext.description, "test.txt (0 B)")
        assertEquals(fileIOSpan.data["file.size"], 0L)
        assertEquals(fileIOSpan.throwable, null)
        assertEquals(fileIOSpan.isFinished, true)
        assertEquals(fileIOSpan.status, SpanStatus.OK)
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
        assertEquals(fileIOSpan.spanContext.description, "test.txt (1 B)")
        assertEquals(fileIOSpan.data["file.size"], 1L)
    }

    @Test
    fun `write array of bytes`() {
        fixture.getSut(tmpFile).use { it.write(ByteArray(10)) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (10 B)")
        assertEquals(fileIOSpan.data["file.size"], 10L)
    }

    @Test
    fun `write array range of bytes`() {
        fixture.getSut(tmpFile).use { it.write(ByteArray(10), 1, 3) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (3 B)")
        assertEquals(fileIOSpan.data["file.size"], 3L)
    }

    @Test
    fun `write all bytes`() {
        fixture.getSut(tmpFile).use { it.write("Text".toByteArray()) }

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
        assertEquals(fileIOSpan.data["file.size"], 4L)
    }

    @Test
    fun `when run on main thread, attaches call_stack with blocked_main_thread=true`() {
        val fis = fixture.getSut(tmpFile)
        fis.close()

        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(true, fileIOSpan.data["blocked_main_thread"])
        assertNotNull(fileIOSpan.data["call_stack"])
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
        assertEquals(false, fileIOSpan.data["blocked_main_thread"])
        assertNull(fileIOSpan.data["call_stack"])
    }
}
