package io.sentry.android.core.anr

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class AnrProfileRotationHelperTest {
  @get:Rule val tmpDir = TemporaryFolder()

  @BeforeTest
  fun setup() {
    AnrProfileRotationHelper.rotate()
  }

  @Test
  fun `getFileForRecording returns file with correct name`() {
    val file = AnrProfileRotationHelper.getFileForRecording(tmpDir.root)

    assertEquals("anr_profile", file.name)
    assertEquals(tmpDir.root, file.parentFile)
  }

  @Test
  fun `getLastFile returns last file`() {
    val file = AnrProfileRotationHelper.getLastFile(tmpDir.root)

    assertEquals("anr_profile_old", file.name)
    assertEquals(tmpDir.root, file.parentFile)
  }

  @Test
  fun `deleteLastFile returns true when file does not exist`() {
    val result = AnrProfileRotationHelper.deleteLastFile(tmpDir.root)

    assertTrue(result)
  }

  @Test
  fun `deleteLastFile returns true when file is deleted successfully`() {
    val lastFile = File(tmpDir.root, "anr_profile_old")
    lastFile.writeText("test content")
    assertTrue(lastFile.exists())

    val result = AnrProfileRotationHelper.deleteLastFile(tmpDir.root)

    assertTrue(result)
    assertFalse(lastFile.exists())
  }

  @Test
  fun `rotate moves current file to last file`() {
    val currentFile = File(tmpDir.root, "anr_profile")
    currentFile.writeText("current content")

    val lastFile = AnrProfileRotationHelper.getLastFile(tmpDir.root)

    assertTrue(lastFile.exists())
    assertEquals("current content", lastFile.readText())
  }

  @Test
  fun `rotate deletes existing last file before moving`() {
    val currentFile = File(tmpDir.root, "anr_profile")
    val lastFile = File(tmpDir.root, "anr_profile_old")

    lastFile.writeText("last content")
    currentFile.writeText("current content")

    assertTrue(lastFile.exists())
    assertTrue(currentFile.exists())

    val newLastFile = AnrProfileRotationHelper.getLastFile(tmpDir.root)

    assertTrue(newLastFile.exists())
    assertEquals("current content", newLastFile.readText())
  }

  @Test
  fun `rotate does not directly perform file renaming`() {
    val currentFile = File(tmpDir.root, "anr_profile")
    currentFile.writeText("current")

    val lastFile = File(tmpDir.root, "anr_profile_old")
    lastFile.writeText("last")

    AnrProfileRotationHelper.rotate()

    // content is still the same
    assertEquals("current", currentFile.readText())
    assertEquals("last", lastFile.readText())

    // but once rotated, the last file should now contain the current file's content
    AnrProfileRotationHelper.getFileForRecording(tmpDir.root)
    assertEquals("current", lastFile.readText())
  }

  @Test
  fun `getFileForRecording triggers rotation when needed`() {
    val currentFile = File(tmpDir.root, "anr_profile")
    currentFile.writeText("content before rotation")

    AnrProfileRotationHelper.getFileForRecording(tmpDir.root)

    val lastFile = File(tmpDir.root, "anr_profile_old")
    assertTrue(lastFile.exists())
    assertEquals("content before rotation", lastFile.readText())
  }
}
