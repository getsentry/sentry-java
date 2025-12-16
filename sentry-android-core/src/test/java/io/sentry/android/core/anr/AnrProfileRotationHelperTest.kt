package io.sentry.android.core.anr

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnrProfileRotationHelperTest {
  private lateinit var tempDir: File

  @BeforeTest
  fun setup() {
    tempDir = Files.createTempDirectory("anr_profile_rotation_test").toFile()
    AnrProfileRotationHelper.rotate()
  }

  @AfterTest
  fun cleanup() {
    if (::tempDir.isInitialized && tempDir.exists()) {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `getFileForRecording returns file with correct name`() {
    val file = AnrProfileRotationHelper.getFileForRecording(tempDir)

    assertEquals("anr_profile", file.name)
    assertEquals(tempDir, file.parentFile)
  }

  @Test
  fun `getLastFile returns last file`() {
    val file = AnrProfileRotationHelper.getLastFile(tempDir)

    assertEquals("anr_profile_old", file.name)
    assertEquals(tempDir, file.parentFile)
  }

  @Test
  fun `deleteLastFile returns true when file does not exist`() {
    val result = AnrProfileRotationHelper.deleteLastFile(tempDir)

    assertTrue(result)
  }

  @Test
  fun `deleteLastFile returns true when file is deleted successfully`() {
    val lastFile = File(tempDir, "anr_profile_old")
    lastFile.writeText("test content")
    assertTrue(lastFile.exists())

    val result = AnrProfileRotationHelper.deleteLastFile(tempDir)

    assertTrue(result)
    assertFalse(lastFile.exists())
  }

  @Test
  fun `rotate moves current file to last file`() {
    val currentFile = File(tempDir, "anr_profile")
    currentFile.writeText("current content")

    val lastFile = AnrProfileRotationHelper.getLastFile(tempDir)

    assertTrue(lastFile.exists())
    assertEquals("current content", lastFile.readText())
  }

  @Test
  fun `rotate deletes existing last file before moving`() {
    val currentFile = File(tempDir, "anr_profile")
    val lastFile = File(tempDir, "anr_profile_old")

    lastFile.writeText("last content")
    currentFile.writeText("current content")

    assertTrue(lastFile.exists())
    assertTrue(currentFile.exists())

    val newLastFile = AnrProfileRotationHelper.getLastFile(tempDir)

    assertTrue(newLastFile.exists())
    assertEquals("current content", newLastFile.readText())
  }

  @Test
  fun `rotate does not directly perform file renaming`() {
    val currentFile = File(tempDir, "anr_profile")
    currentFile.writeText("current")

    val lastFile = File(tempDir, "anr_profile_old")
    lastFile.writeText("last")

    AnrProfileRotationHelper.rotate()

    // content is still the same
    assertEquals("current", currentFile.readText())
    assertEquals("last", lastFile.readText())

    // but once rotated, the last file should now contain the current file's content
    AnrProfileRotationHelper.getFileForRecording(tempDir)
    assertEquals("current", lastFile.readText())
  }

  @Test
  fun `getFileForRecording triggers rotation when needed`() {
    val currentFile = File(tempDir, "anr_profile")
    currentFile.writeText("content before rotation")

    AnrProfileRotationHelper.getFileForRecording(tempDir)

    val lastFile = File(tempDir, "anr_profile_old")
    assertTrue(lastFile.exists())
    assertEquals("content before rotation", lastFile.readText())
  }
}
