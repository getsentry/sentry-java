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

class SentryFileReaderTest {
  class Fixture {
    val scopes = mock<IScopes>()
    lateinit var sentryTracer: SentryTracer

    internal fun getSut(
      tmpFile: File,
      activeTransaction: Boolean = true,
      optionsConfiguration: (SentryOptions) -> Unit = {},
    ): SentryFileReader {
      tmpFile.writeText("TEXT")
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
      return SentryFileReader(tmpFile, scopes)
    }
  }

  @get:Rule val tmpDir = TemporaryFolder()

  private val fixture = Fixture()

  private val tmpFile: File
    get() = tmpDir.newFile("test.txt")

  private val tmpFileWithoutExtension: File
    get() = tmpDir.newFile("test")

  @Test
  fun `captures a span`() {
    val reader = fixture.getSut(tmpFile)
    reader.readText()
    reader.close()

    assertEquals(fixture.sentryTracer.children.size, 1)
    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.operation, "file.read")
    assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
    assertNotNull(fileIOSpan.data["file.path"])
    assertEquals(fileIOSpan.data["file.size"], 4L)
    assertEquals(fileIOSpan.throwable, null)
    assertEquals(fileIOSpan.isFinished, true)
    assertEquals(fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY], true)
    assertEquals(fileIOSpan.status, OK)
  }

  @Test
  fun `configured file paths false omits file path`() {
    val reader = fixture.getSut(tmpFile) { it.dataCollection.setFilePaths(false) }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file extension in description when file paths are disabled`() {
    val reader = fixture.getSut(tmpFile) { it.dataCollection.setFilePaths(false) }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file size if no extension is available and file paths are disabled`() {
    val reader =
      fixture.getSut(tmpFileWithoutExtension) {
        it.dataCollection.setFilePaths(false)
      }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.sentryTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "*** (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }
}
