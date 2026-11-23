package io.sentry.android.core.anr

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryOptions
import io.sentry.test.getProperty
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AnrProfileStoreTest {

  private lateinit var cacheDir: File
  private lateinit var options: SentryOptions
  private lateinit var store: AnrProfileStore

  @BeforeTest
  fun setUp() {
    cacheDir = Files.createTempDirectory("anr-profile-store").toFile()
    // Rotation moves an existing file once per process, do it before any file is seeded.
    AnrProfileRotationHelper.getFileForRecording(cacheDir)
    options = SentryOptions()
    options.cacheDirPath = cacheDir.absolutePath
    store = AnrProfileStore(options)
  }

  @AfterTest
  fun tearDown() {
    store.close()
    cacheDir.deleteRecursively()
  }

  private fun file() = File(cacheDir, "anr_profile")

  private fun readFile(): List<AnrStackTrace> {
    // The store keeps the queue open, so release it before reading from a second manager.
    store.close()
    AnrProfileManager(options, file()).use {
      return it.load().stacks
    }
  }

  @Test
  fun `clear and add write the samples to the ANR profile file`() {
    store.clear()
    store.add(AnrStackTrace(1, emptyArray()))
    store.add(AnrStackTrace(2, emptyArray()))

    assertThat(readFile().map { it.timestampMs }).containsExactly(1L, 2L).inOrder()
  }

  @Test
  fun `clear removes the samples of an earlier hang`() {
    AnrProfileManager(options, file()).use { it.add(AnrStackTrace(1, emptyArray())) }

    store.clear()
    store.add(AnrStackTrace(2, emptyArray()))

    assertThat(readFile().map { it.timestampMs }).containsExactly(2L)
  }

  @Test
  fun `the file is opened at the first write`() {
    assertThat(file().exists()).isFalse()

    store.clear()

    assertThat(file().exists()).isTrue()
  }

  @Test
  fun `the file is opened once`() {
    store.clear()
    val manager = store.getProperty<AnrProfileManager>("profileManager")

    store.add(AnrStackTrace(1, emptyArray()))

    assertThat(store.getProperty<AnrProfileManager>("profileManager")).isSameInstanceAs(manager)
  }

  @Test
  fun `writes nothing without cacheDirPath`() {
    options.cacheDirPath = null

    store.clear()
    store.add(AnrStackTrace(1, emptyArray()))

    assertThat(file().exists()).isFalse()
  }

  @Test
  fun `close without a write creates no file`() {
    store.close()

    assertThat(file().exists()).isFalse()
  }

  @Test
  fun `close releases the file`() {
    store.add(AnrStackTrace(1, emptyArray()))

    store.close()
    store.close()

    assertThat(store.getProperty<AnrProfileManager?>("profileManager")).isNull()
    AnrProfileManager(options, file()).use {
      assertThat(it.load().stacks.map { stack -> stack.timestampMs }).containsExactly(1L)
    }
  }
}
