package io.sentry.util

import java.io.File
import java.nio.file.Files
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
}
