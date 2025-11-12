package io.sentry.android.core.anr

import io.sentry.SentryOptions
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class AnrProfileManagerTest {
  private lateinit var tempDir: File

  @AfterTest
  fun cleanup() {
    if (::tempDir.isInitialized && tempDir.exists()) {
      tempDir.deleteRecursively()
    }
  }

  private fun createOptions(): SentryOptions {
    tempDir = Files.createTempDirectory("anr_profile_test").toFile()
    val options = SentryOptions()
    options.cacheDirPath = tempDir.absolutePath
    options.setLogger(mock())
    return options
  }

  @Test
  fun `can add and load stack traces`() {
    // Arrange
    val options = createOptions()
    val manager = AnrProfileManager(options)
    val stackTraceElements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )
    val trace = AnrStackTrace(1000, stackTraceElements)

    // Act
    manager.add(trace)
    val profile = manager.load()

    // Assert
    assertNotNull(profile)
    assertEquals(1, profile.stacks.size)
    assertEquals(1000L, profile.stacks[0].timestampMs)
    assertEquals(2, profile.stacks[0].stack.size)
  }

  @Test
  fun `can add multiple stack traces`() {
    // Arrange
    val options = createOptions()
    val manager = AnrProfileManager(options)
    val stackTraceElements1 =
      arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))
    val stackTraceElements2 =
      arrayOf(StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100))

    // Act
    manager.add(AnrStackTrace(1000, stackTraceElements1))
    manager.add(AnrStackTrace(2000, stackTraceElements2))
    val profile = manager.load()

    // Assert
    assertNotNull(profile)
    assertEquals(2, profile.stacks.size)
    assertEquals(1000L, profile.stacks[0].timestampMs)
    assertEquals(2000L, profile.stacks[1].timestampMs)
  }

  @Test
  fun `can clear all stack traces`() {
    // Arrange
    val options = createOptions()
    val manager = AnrProfileManager(options)
    val stackTraceElements =
      arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))
    manager.add(AnrStackTrace(1000, stackTraceElements))

    // Act
    manager.clear()
    val profile = manager.load()

    // Assert
    assertTrue(profile.stacks.isEmpty())
  }

  @Test
  fun `load empty profile when nothing added`() {
    // Arrange
    val options = createOptions()
    val manager = AnrProfileManager(options)

    // Act
    val profile = manager.load()

    // Assert
    assertNotNull(profile)
    assertTrue(profile.stacks.isEmpty())
  }

  @Test
  fun `can deal with corrupt files`() {
    // Arrange
    val options = createOptions()

    val file = File(options.getCacheDirPath(), "anr_profile")
    file.writeBytes("Hello World".toByteArray())

    val manager = AnrProfileManager(options)

    // Act
    val profile = manager.load()

    // Assert
    assertNotNull(profile)
    assertTrue(profile.stacks.isEmpty())
  }

  @Test
  fun `persists profiles across manager instances`() {
    // Arrange
    val options = createOptions()
    val stackTraceElements =
      arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    // Act - add profile with first manager
    var manager = AnrProfileManager(options)
    manager.add(AnrStackTrace(1000, stackTraceElements))

    // Create new manager instance from same cache dir
    manager = AnrProfileManager(options)
    val profile = manager.load()

    // Assert
    assertNotNull(profile)
    assertEquals(1, profile.stacks.size)
    assertEquals(1000L, profile.stacks[0].timestampMs)
  }
}
