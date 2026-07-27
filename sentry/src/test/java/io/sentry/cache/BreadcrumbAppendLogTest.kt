package io.sentry.cache

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.NoOpLogger
import io.sentry.SentryOptions
import java.io.File
import kotlin.test.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BreadcrumbAppendLogTest {
  @get:Rule val tmpDir = TemporaryFolder()

  private fun options(maxBreadcrumbs: Int = 100) =
    SentryOptions().apply {
      setLogger(NoOpLogger.getInstance())
      setMaxBreadcrumbs(maxBreadcrumbs)
    }

  private fun sut(options: SentryOptions = options(), file: File? = null) =
    BreadcrumbAppendLog(options, file ?: File(tmpDir.newFolder(), "breadcrumbs.json"))

  @Test
  fun `round-trips appended breadcrumbs in order`() {
    val log = sut()

    log.append(listOf(Breadcrumb.debug("one"), Breadcrumb.debug("two")))

    assertThat(log.read().map { it.message }).containsExactly("one", "two").inOrder()
  }

  @Test
  fun `appends across separate calls accumulate`() {
    val log = sut()

    log.append(listOf(Breadcrumb.debug("one")))
    log.append(listOf(Breadcrumb.debug("two")))

    assertThat(log.read().map { it.message }).containsExactly("one", "two").inOrder()
  }

  @Test
  fun `reads back nothing when the file does not exist`() {
    assertThat(sut().read()).isEmpty()
  }

  @Test
  fun `clear empties the log`() {
    val log = sut()
    log.append(listOf(Breadcrumb.debug("one")))

    log.clear()

    assertThat(log.read()).isEmpty()
  }

  @Test
  fun `appends after a clear are retained`() {
    val log = sut()
    log.append(listOf(Breadcrumb.debug("one")))
    log.clear()

    log.append(listOf(Breadcrumb.debug("two")))

    assertThat(log.read().map { it.message }).containsExactly("two")
  }

  @Test
  fun `read is bounded to maxBreadcrumbs, keeping the newest`() {
    val log = sut(options(maxBreadcrumbs = 3))

    log.append((1..5).map { Breadcrumb.debug("crumb-$it") })

    assertThat(log.read().map { it.message })
      .containsExactly("crumb-3", "crumb-4", "crumb-5")
      .inOrder()
  }

  @Test
  fun `compacts the file once it outgrows the threshold`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    val log = BreadcrumbAppendLog(options(maxBreadcrumbs = 2), file)

    // threshold is maxBreadcrumbs * 2, so the 5th crumb triggers compaction
    (1..5).forEach { log.append(listOf(Breadcrumb.debug("crumb-$it"))) }

    assertThat(file.readLines().filter { it.isNotEmpty() }).hasSize(2)
    assertThat(log.read().map { it.message }).containsExactly("crumb-4", "crumb-5").inOrder()
  }

  @Test
  fun `skips a torn trailing line and keeps earlier breadcrumbs`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    val log = BreadcrumbAppendLog(options(), file)
    log.append(listOf(Breadcrumb.debug("one"), Breadcrumb.debug("two")))

    // simulate the process dying mid-write, leaving a partial JSON object on the last line
    file.appendText("{\"message\":\"thr")

    assertThat(log.read().map { it.message }).containsExactly("one", "two").inOrder()
  }

  @Test
  fun `skips a corrupt line in the middle of the log`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    val log = BreadcrumbAppendLog(options(), file)
    log.append(listOf(Breadcrumb.debug("one")))
    file.appendText("not json at all\n")
    log.append(listOf(Breadcrumb.debug("two")))

    assertThat(log.read().map { it.message }).containsExactly("one", "two").inOrder()
  }

  @Test
  fun `bounds a log inherited from a previous run`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    BreadcrumbAppendLog(options(maxBreadcrumbs = 2), file)
      .append((1..4).map { Breadcrumb.debug("old-$it") })

    // a fresh instance has no in-memory line count, so it must recount before appending
    val reopened = BreadcrumbAppendLog(options(maxBreadcrumbs = 2), file)
    reopened.append(listOf(Breadcrumb.debug("new")))

    assertThat(file.readLines().filter { it.isNotEmpty() }).hasSize(2)
    assertThat(reopened.read().map { it.message }).containsExactly("old-4", "new").inOrder()
  }

  @Test
  fun `appending nothing does not create the file`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")

    BreadcrumbAppendLog(options(), file).append(emptyList())

    assertThat(file.exists()).isFalse()
  }

  @Test
  fun `discards a legacy JSON-array file on read`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    file.writeText("""[{"message":"old","type":"debug"}]""")

    assertThat(BreadcrumbAppendLog(options(), file).read()).isEmpty()
    assertThat(file.exists()).isFalse()
  }

  @Test
  fun `discards a legacy binary file before appending`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    // a QueueFile starts with a binary header, not '{'
    file.writeBytes(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x01, 0x00, 0x00))
    val log = BreadcrumbAppendLog(options(), file)

    log.append(listOf(Breadcrumb.debug("new")))

    assertThat(log.read().map { it.message }).containsExactly("new")
  }

  @Test
  fun `no-ops without a file`() {
    val log = BreadcrumbAppendLog(options(), null)

    log.append(listOf(Breadcrumb.debug("one")))
    log.clear()

    assertThat(log.read()).isEmpty()
  }

  @Test
  fun `newlines inside breadcrumb data do not break line framing`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    val log = BreadcrumbAppendLog(options(), file)

    log.append(
      listOf(
        Breadcrumb.debug("first\nsecond\r\nthird").apply { setData("key", "a\nb") },
        Breadcrumb.debug("after"),
      )
    )

    assertThat(file.readLines().filter { it.isNotEmpty() }).hasSize(2)
    assertThat(log.read().map { it.message })
      .containsExactly("first\nsecond\r\nthird", "after")
      .inOrder()
  }

  @Test
  fun `tolerates a non-positive maxBreadcrumbs`() {
    val file = File(tmpDir.newFolder(), "breadcrumbs.json")
    val log = BreadcrumbAppendLog(options(maxBreadcrumbs = 0), file)

    log.append(listOf(Breadcrumb.debug("one"), Breadcrumb.debug("two")))

    assertThat(log.read().map { it.message }).containsExactly("two")
  }
}
