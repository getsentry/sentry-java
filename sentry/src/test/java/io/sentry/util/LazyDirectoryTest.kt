package io.sentry.util

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.test.Test

class LazyDirectoryTest {
  @Test
  fun `getFile does not create the directory`() {
    val path = Files.createTempDirectory("lazy-dir-test").resolve("outbox")
    val lazyDirectory = LazyDirectory(path.toString())

    assertThat(lazyDirectory.file.exists()).isFalse()
  }

  @Test
  fun `getOrCreate creates the directory and any missing parents`() {
    val path = Files.createTempDirectory("lazy-dir-test").resolve("nested").resolve("outbox")
    val lazyDirectory = LazyDirectory(path.toString())

    val created = lazyDirectory.getOrCreate()

    assertThat(created.isDirectory).isTrue()
    assertThat(created.absolutePath).isEqualTo(path.toFile().absolutePath)
  }

  @Test
  fun `getOrCreate is idempotent when the directory already exists`() {
    val path = Files.createTempDirectory("lazy-dir-test").resolve("outbox")
    val lazyDirectory = LazyDirectory(path.toString())

    assertThat(lazyDirectory.getOrCreate().isDirectory).isTrue()
    assertThat(lazyDirectory.getOrCreate().isDirectory).isTrue()
  }
}
