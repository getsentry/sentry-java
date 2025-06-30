package io.sentry.instrumentation.file

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus.OK
import io.sentry.TransactionContext
import io.sentry.util.thread.ThreadChecker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentryFileWriterTest {
  class Fixture {
    val scopes = mock<IScopes>()
    lateinit var sentryTracer: SentryTracer

    internal fun getSut(
      tmpFile: File,
      activeTransaction: Boolean = true,
      append: Boolean = false,
      optionsConfiguration: (SentryOptions) -> Unit = {},
    ): SentryFileWriter {
      val options =
        SentryOptions().apply {
          threadChecker = ThreadChecker.getInstance()
          optionsConfiguration(this)
        }
      whenever(scopes.options).thenReturn(options)
      sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
      if (activeTransaction) {
        whenever(scopes.span).thenReturn(sentryTracer)
      }
      return SentryFileWriter(tmpFile, append, scopes)
    }
  }

  @get:Rule val tmpDir = TemporaryFolder()

  private val fixture = Fixture()

  private val tmpFile: File by lazy { tmpDir.newFile("test.txt") }

  private val tmpFileWithoutExtension: File by lazy { tmpDir.newFile("test") }

  @Test
  fun `captures a span`() {
    val writer = fixture.getSut(tmpFile)
    writer.write("TEXT")
    writer.close()

    assertEquals(fixture.sentryTracer.children.size, 1)
    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.operation, "file.write")
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertEquals(fileIOSpan.data["file.size"], 4L)
    assertEquals(fileIOSpan.throwable, null)
    assertEquals(fileIOSpan.isFinished, true)
    assertEquals(fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY], true)
    assertEquals(fileIOSpan.status, OK)
  }

  @Test
  fun `append works`() {
    val writer1 = fixture.getSut(tmpFile, append = true)
    writer1.use { it.append("test") }

    // second writer should not overwrite the file contents, but append to the existing content
    val writer2 = fixture.getSut(tmpFile, append = true)
    writer2.use { it.append("test2") }

    assertEquals("testtest2", tmpFile.readText())
  }

  @Test
  fun `captures file name in description and file path when isSendDefaultPii is true`() {
    val writer = fixture.getSut(tmpFile) { it.isSendDefaultPii = true }
    writer.write("TEXT")
    writer.close()

    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
    assertNotNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file extension in description when isSendDefaultPii is false`() {
    val writer = fixture.getSut(tmpFile) { it.isSendDefaultPii = false }
    writer.write("TEXT")
    writer.close()

    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file size if no extension is available when isSendDefaultPii is false`() {
    val writer = fixture.getSut(tmpFileWithoutExtension) { it.isSendDefaultPii = false }
    writer.write("TEXT")
    writer.close()

    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "*** (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }
}
