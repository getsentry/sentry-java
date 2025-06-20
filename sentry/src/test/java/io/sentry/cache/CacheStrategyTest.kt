package io.sentry.cache

import io.sentry.DataCategory
import io.sentry.DateUtils
import io.sentry.JsonSerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.clientreport.ClientReportTestHelper.Companion.assertClientReport
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class CacheStrategyTest {
  private class Fixture {
    val dir = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    val sentryOptions = SentryOptions().apply { setSerializer(mock()) }

    fun getSUT(maxSize: Int = 5, options: SentryOptions = sentryOptions): CacheStrategy =
      CustomCache(options, dir.absolutePath, maxSize)
  }

  private val fixture = Fixture()

  @Test
  fun `isDirectoryValid returns true if a valid directory`() {
    val sut = fixture.getSUT()

    // sanity check
    assertTrue(fixture.dir.isDirectory)

    // this test assumes that the dir. has write/read permission.
    assertTrue(sut.isDirectoryValid)
  }

  @Test
  fun `Sort files from the oldest to the newest`() {
    val sut = fixture.getSUT(3)

    val files = createTempFilesSortByOldestToNewest()
    val reverseFiles = files.reversedArray()

    sut.rotateCacheIfNeeded(reverseFiles)

    assertEquals(files[0].absolutePath, reverseFiles[0].absolutePath)
    assertEquals(files[1].absolutePath, reverseFiles[1].absolutePath)
    assertEquals(files[2].absolutePath, reverseFiles[2].absolutePath)
  }

  @Test
  fun `Rotate cache folder to save new file`() {
    val sut = fixture.getSUT(3)

    val files = createTempFilesSortByOldestToNewest()
    val reverseFiles = files.reversedArray()

    sut.rotateCacheIfNeeded(reverseFiles)

    assertFalse(files[0].exists())
    assertTrue(files[1].exists())
    assertTrue(files[2].exists())
  }

  @Test
  fun `do not move init flag if state is not ok`() {
    val sut = fixture.getSUT(3, getOptionsWithRealSerializer())

    val files = createTempFilesSortByOldestToNewest()

    saveSessionToFile(files[0], sut, Session.State.Crashed, null)

    saveSessionToFile(files[1], sut, Session.State.Exited, null)

    saveSessionToFile(files[2], sut, Session.State.Exited, null)

    sut.rotateCacheIfNeeded(files)

    // files[0] has been deleted because of rotation
    for (i in 1..2) {
      val expectedSession = getSessionFromFile(files[i], sut)

      assertNull(expectedSession.init)
    }
  }

  @Test
  fun `move init flag if state is ok`() {
    val options = SentryOptions().apply { setSerializer(JsonSerializer(this)) }
    val sut = fixture.getSUT(3, options)

    val files = createTempFilesSortByOldestToNewest()

    val okSession = createSessionMockData(Session.State.Ok, true)
    val okEnvelope = SentryEnvelope.from(sut.serializer.value, okSession, null)
    sut.serializer.value.serialize(okEnvelope, files[0].outputStream())

    val updatedOkSession = okSession.clone()
    updatedOkSession.update(null, null, true)
    val updatedOkEnvelope = SentryEnvelope.from(sut.serializer.value, updatedOkSession, null)
    sut.serializer.value.serialize(updatedOkEnvelope, files[1].outputStream())

    saveSessionToFile(files[2], sut, Session.State.Exited, null)

    sut.rotateCacheIfNeeded(files)

    // files[1] should be the one with the init flag true
    val expectedSession = getSessionFromFile(files[1], sut)

    assertTrue(expectedSession.init!!)

    assertClientReport(
      options.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.CACHE_OVERFLOW.reason, DataCategory.Session.category, 1)),
    )
  }

  @AfterTest
  fun shutdown() {
    fixture.dir.listFiles()?.forEach { it.deleteRecursively() }
  }

  private class CustomCache(options: SentryOptions, path: String, maxSize: Int) :
    CacheStrategy(options, path, maxSize)

  private fun createTempFilesSortByOldestToNewest(): Array<File> {
    val f1 = Files.createTempFile(fixture.dir.toPath(), "f1", ".json").toFile()
    f1.setLastModified(DateUtils.getDateTime("2020-03-27T08:52:58.015Z").time)

    val f2 = Files.createTempFile(fixture.dir.toPath(), "f2", ".json").toFile()
    f2.setLastModified(DateUtils.getDateTime("2020-03-27T08:52:59.015Z").time)

    val f3 = Files.createTempFile(fixture.dir.toPath(), "f3", ".json").toFile()
    f3.setLastModified(DateUtils.getDateTime("2020-03-27T08:53:00.015Z").time)

    return arrayOf(f1, f2, f3)
  }

  private fun createSessionMockData(
    state: Session.State = Session.State.Ok,
    init: Boolean? = true,
  ): Session =
    Session(
      state,
      DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
      DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
      2,
      "123",
      "c81d4e2ebcf211e6869b7df92533d2db",
      init,
      123456.toLong(),
      6000.toDouble(),
      "127.0.0.1",
      "jamesBond",
      "debug",
      "io.sentry@1.0+123",
      null,
    )

  private fun getSessionFromFile(file: File, sut: CacheStrategy): Session {
    val envelope = sut.serializer.value.deserializeEnvelope(file.inputStream())
    val item = envelope!!.items.first()

    val reader = InputStreamReader(ByteArrayInputStream(item.data), Charsets.UTF_8)
    return sut.serializer.value.deserialize(reader, Session::class.java)!!
  }

  private fun saveSessionToFile(
    file: File,
    sut: CacheStrategy,
    state: Session.State = Session.State.Ok,
    init: Boolean? = true,
  ) {
    val okSession = createSessionMockData(state, init)
    val okEnvelope = SentryEnvelope.from(sut.serializer.value, okSession, null)
    sut.serializer.value.serialize(okEnvelope, file.outputStream())
  }

  private fun getOptionsWithRealSerializer(): SentryOptions =
    SentryOptions().apply { setSerializer(JsonSerializer(this)) }
}
