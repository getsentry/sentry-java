package io.sentry.util

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileUtilsTest {
  @Test
  fun `deleteRecursively returns true on null file`() {
    assertTrue(FileUtils.deleteRecursively(null))
  }

  @Test
  fun `deleteRecursively returns true on non-existing file`() {
    assertTrue(FileUtils.deleteRecursively(File("")))
  }

  @Test
  fun `deleteRecursively deletes a simple file`() {
    val f = Files.createTempFile("here", "test").toFile()
    assertTrue(f.exists())
    assertTrue(FileUtils.deleteRecursively(f))
    assertFalse(f.exists())
  }

  @Test
  fun `deleteRecursively deletes a folder`() {
    // Setup vars
    val rootDir = Files.createTempDirectory("here").toFile()
    val rootChild = File(rootDir, "test")
    val innerDir = File(rootDir, "dir2")
    val innerChild = File(innerDir, "test")

    // Create directories and files
    rootChild.createNewFile()
    innerDir.mkdir()
    innerChild.createNewFile()

    // Assert dirs and files exist
    assertTrue(rootDir.exists() && rootDir.isDirectory)
    assertTrue(rootChild.exists())
    assertTrue(innerDir.exists() && innerDir.isDirectory)
    assertTrue(innerChild.exists())

    // Assert deletion returns true
    assertTrue(FileUtils.deleteRecursively(rootDir))

    // Assert dirs and files no longer exist
    assertFalse(rootChild.exists())
    assertFalse(rootDir.exists())
    assertFalse(innerChild.exists())
    assertFalse(innerDir.exists())
  }

  @Test
  fun `readText returns null on null, non existing or unreadable file`() {
    val f = File("here", "test")
    val unreadableFile = Files.createTempFile("here", "test").toFile()
    unreadableFile.setReadable(false)
    assertNull(FileUtils.readText(null))
    assertNull(FileUtils.readText(f))
    assertNull(FileUtils.readText(unreadableFile))
  }

  @Test
  fun `readText returns the content of a file`() {
    val f = Files.createTempFile("here", "test").toFile()
    val text = "Lorem ipsum dolor sit amet\nLorem ipsum dolor sit amet"
    f.writeText(text)
    assertEquals(text, FileUtils.readText(f))
  }

  @Test
  fun `createDirectory creates the directory and any missing parents`() {
    val dir = File(Files.createTempDirectory("create-dir-test").toFile(), "nested/outbox")

    assertThat(FileUtils.createDirectory(dir)).isTrue()
    assertThat(dir.isDirectory).isTrue()
  }

  @Test
  fun `createDirectory returns true when the directory already exists`() {
    val dir = Files.createTempDirectory("create-dir-test").toFile()

    assertThat(FileUtils.createDirectory(dir)).isTrue()
  }

  @Test
  fun `createDirectory returns false when the directory cannot be created`() {
    val file = Files.createTempFile("create-dir-test", "test").toFile()

    // a regular file already occupies the path, so it can never become a directory
    assertThat(FileUtils.createDirectory(file)).isFalse()
  }

  @Test
  fun `createDirectory returns true for every caller when threads race to create it`() {
    val threadCount = 8
    // mkdirs() returns false for the losers of the race, so every caller must still see success
    repeat(50) { iteration ->
      val dir = File(Files.createTempDirectory("create-dir-race").toFile(), "run$iteration/outbox")
      val barrier = CyclicBarrier(threadCount)
      val results = ConcurrentLinkedQueue<Boolean>()

      val threads =
        (1..threadCount).map {
          Thread {
            barrier.await()
            results.add(FileUtils.createDirectory(dir))
          }
        }
      threads.forEach { it.start() }
      threads.forEach { it.join() }

      assertThat(results).hasSize(threadCount)
      assertThat(results).doesNotContain(false)
    }
  }
}
