package io.sentry.instrumentation.file

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus.OK
import io.sentry.TransactionContext
import io.sentry.util.thread.ThreadChecker
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryFileReaderTest {
    class Fixture {
        val scopes = mock<IScopes>()
        lateinit var sentryTracer: SentryTracer

        internal fun getSut(
            tmpFile: File,
            activeTransaction: Boolean = true
        ): SentryFileReader {
            tmpFile.writeText("TEXT")
            whenever(scopes.options).thenReturn(
                SentryOptions().apply {
                    threadChecker = ThreadChecker.getInstance()
                }
            )
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
            if (activeTransaction) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }
            return SentryFileReader(tmpFile, scopes)
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
        assertEquals(fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY], true)
        assertEquals(fileIOSpan.status, OK)
    }
}
