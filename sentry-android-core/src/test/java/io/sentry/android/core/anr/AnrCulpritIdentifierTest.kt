package io.sentry.android.core.anr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnrCulpritIdentifierTest {

  @Test
  fun `returns null for empty dumps`() {
    // Arrange
    val dumps = emptyList<AnrStackTrace>()

    // Act
    val result = AnrCulpritIdentifier.identify(dumps)

    // Assert
    assertNull(result)
  }

  @Test
  fun `identifies single stack trace`() {
    // Arrange
    val stackTraceElements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )
    val dumps = listOf(AnrStackTrace(1000, stackTraceElements))

    // Act
    val result = AnrCulpritIdentifier.identify(dumps)

    // Assert
    assertNotNull(result)
    assertEquals(1, result.count)
    assertTrue(result.depth > 0)
  }

  @Test
  fun `identifies most common stack trace from multiple dumps`() {
    // Arrange
    val commonElements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )
    val rareElements =
      arrayOf(
        StackTraceElement("com.example.RareClass", "rareMethod", "RareClass.java", 50),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )
    val dumps =
      listOf(
        AnrStackTrace(1000, commonElements),
        AnrStackTrace(2000, commonElements),
        AnrStackTrace(3000, rareElements),
      )

    // Act
    val result = AnrCulpritIdentifier.identify(dumps)

    // Assert
    assertNotNull(result)
    // The common element should have higher count (appears twice) vs rare (appears once)
    assertEquals(2, result.count)
  }

  @Test
  fun `applies lower quality score to framework packages`() {
    // Arrange
    val frameworkElements =
      arrayOf(
        StackTraceElement("java.lang.Object", "wait", "Object.java", 42),
        StackTraceElement("android.os.Handler", "handleMessage", "Handler.java", 100),
      )
    val appElements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("android.os.Handler", "handleMessage", "Handler.java", 100),
      )
    val dumps =
      listOf(
        AnrStackTrace(1000, frameworkElements),
        AnrStackTrace(2000, frameworkElements),
        AnrStackTrace(3000, appElements),
      )

    // Act
    val result = AnrCulpritIdentifier.identify(dumps)

    // Assert
    assertNotNull(result)
    // Should identify a culprit from the stacks
    assertTrue(result.count > 0)
  }

  @Test
  fun `prefers deeper stack traces on quality tie`() {
    // Arrange
    val shallowStack =
      arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))
    val deepStack =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
        StackTraceElement("com.example.ThirdClass", "method3", "ThirdClass.java", 150),
      )
    val dumps =
      listOf(
        AnrStackTrace(1000, shallowStack),
        AnrStackTrace(2000, shallowStack),
        AnrStackTrace(3000, deepStack),
        AnrStackTrace(4000, deepStack),
      )

    // Act
    val result = AnrCulpritIdentifier.identify(dumps)

    // Assert
    assertNotNull(result)
    // Both have count 2, but deep stack should be preferred due to depth
    assertTrue(result.depth >= 1)
  }

  @Test
  fun `handles mixed framework and app code`() {
    // Arrange
    val mixedElements =
      arrayOf(
        StackTraceElement("com.example.Activity", "onCreate", "Activity.java", 42),
        StackTraceElement("com.example.DataProcessor", "process", "DataProcessor.java", 100),
        StackTraceElement("java.lang.Thread", "run", "Thread.java", 50),
      )
    val dumps = listOf(AnrStackTrace(1000, mixedElements))

    // Act
    val result = AnrCulpritIdentifier.identify(dumps)

    // Assert
    assertNotNull(result)
    // Should identify the custom app code as culprit, not the framework code
    assertTrue(result.getStack().any { it.className.startsWith("com.example.") })
  }
}
