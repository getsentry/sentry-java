package io.sentry.instrumentation.file

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus.OK
import io.sentry.TransactionContext
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryFileReaderTest {
    class Fixture {
        val hub = mock<IHub>()
        lateinit var sentryTracer: SentryTracer

        internal fun getSut(
            tmpFile: File,
            activeTransaction: Boolean = true,
        ): SentryFileReader {
            tmpFile.writeText("TEXT")
            whenever(hub.options).thenReturn(SentryOptions())
            sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)
            if (activeTransaction) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            return SentryFileReader(tmpFile, hub)
        }
    }

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val fixture = Fixture()

    private val tmpFile: File get() = tmpDir.newFile("test.txt")

    @Test
    fun `captures a span`() {
        val reader = fixture.getSut(tmpFile)
        reader.readText()
        reader.close()

        assertEquals(fixture.sentryTracer.children.size, 1)
        val fileIOSpan = fixture.sentryTracer.children.first()
        assertEquals(fileIOSpan.spanContext.operation, "file.read")
        assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
        assertEquals(fileIOSpan.data["file.size"], 4L)
        assertEquals(fileIOSpan.throwable, null)
        assertEquals(fileIOSpan.isFinished, true)
        assertEquals(fileIOSpan.status, OK)
    }
}
