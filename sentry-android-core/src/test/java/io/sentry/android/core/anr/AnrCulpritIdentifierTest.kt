package io.sentry.android.core.anr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnrCulpritIdentifierTest {

  @Test
  fun `returns null for empty dumps`() {
    val dumps = emptyList<AnrStackTrace>()
    val result = AnrCulpritIdentifier.identify(dumps)
    assertNull(result)
  }

  @Test
  fun `identifies single stack trace`() {
    val stackTraceElements =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
      )
    val dumps = listOf(AnrStackTrace(1000, stackTraceElements))

    val result = AnrCulpritIdentifier.identify(dumps)

    assertNotNull(result)
    assertEquals(1, result.count)
    assertEquals("com.example.MyClass", result.stack.first().className)
    assertEquals(2, result.depth)
  }

  @Test
  fun `identifies most common, most detailed stack trace from multiple dumps`() {
    val commonElements =
      arrayOf(
        StackTraceElement("com.example.CommonClass", "commonMethod1", "CommonClass.java", 42),
        StackTraceElement("com.example.CommonClass", "commonMethod2", "CommonClass.java", 100),
      )
    val rareElements =
      arrayOf(
        StackTraceElement("com.example.RareClass", "rareMethod", "RareClass.java", 50),
        StackTraceElement("com.example.CommonClass", "commonMethod2", "CommonClass.java", 100),
      )
    val dumps =
      listOf(
        AnrStackTrace(1000, commonElements),
        AnrStackTrace(2000, commonElements),
        AnrStackTrace(3000, rareElements),
      )

    val result = AnrCulpritIdentifier.identify(dumps)

    assertNotNull(result)
    assertEquals(2, result.count)
    assertEquals("com.example.CommonClass", result.stack.first().className)
    assertEquals("commonMethod1", result.stack.first().methodName)
  }

  @Test
  fun `provides 0 quality score when stack only contains framework packages`() {
    val frameworkElements =
      arrayOf(
        StackTraceElement("java.lang.Object", "wait", "Object.java", 42),
        StackTraceElement("android.os.Handler", "handleMessage", "Handler.java", 100),
      )
    val dumps =
      listOf(AnrStackTrace(1000, frameworkElements), AnrStackTrace(2000, frameworkElements))

    val result = AnrCulpritIdentifier.identify(dumps)

    assertNotNull(result)
    assertEquals(0f, result.quality)
  }

  @Test
  fun `applies lower quality score to framework packages`() {
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

    val result = AnrCulpritIdentifier.identify(dumps)

    assertNotNull(result)
    assertEquals("com.example.MyClass", result.stack.first().className)
  }

  @Test
  fun `prefers deeper stack traces`() {
    val shallowStack =
      arrayOf(StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42))

    val deepStack =
      arrayOf(
        StackTraceElement("com.example.MyClass", "method1", "MyClass.java", 42),
        StackTraceElement("com.example.AnotherClass", "method2", "AnotherClass.java", 100),
        StackTraceElement("com.example.ThirdClass", "method3", "ThirdClass.java", 150),
      )
    val dumps = listOf(AnrStackTrace(1000, shallowStack), AnrStackTrace(2000, deepStack))

    val result = AnrCulpritIdentifier.identify(dumps)

    assertNotNull(result)
    assertEquals(3, result.depth)
    assertEquals("com.example.MyClass", result.stack.first().className)
  }

  @Test
  fun `handles mixed framework and app code`() {
    val mixedElements =
      arrayOf(
        StackTraceElement("com.example.Activity", "onCreate", "Activity.java", 42),
        StackTraceElement("com.example.DataProcessor", "process", "DataProcessor.java", 100),
        StackTraceElement("java.lang.Thread", "run", "Thread.java", 50),
      )
    val dumps = listOf(AnrStackTrace(1000, mixedElements))

    val result = AnrCulpritIdentifier.identify(dumps)

    assertNotNull(result)
    assertEquals(2f / 3f, result.quality, 0.0001f)
    assertEquals("com.example.Activity", result.stack.first().className)
  }

  @Test
  fun `isSystemFrame returns true for java lang packages`() {
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("java.lang.Object"))
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("java.lang.Thread"))
  }

  @Test
  fun `isSystemFrame returns true for java util packages`() {
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("java.util.ArrayList"))
  }

  @Test
  fun `isSystemFrame returns true for android packages`() {
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("android.app.Activity"))
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("android.os.Handler"))
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("android.os.Looper"))
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("android.view.View"))
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("android.widget.TextView"))
  }

  @Test
  fun `isSystemFrame returns true for internal android packages`() {
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("com.android.internal.os.ZygoteInit"))
    assertEquals(true, AnrCulpritIdentifier.isSystemFrame("com.google.android.gms.common.api.Api"))
  }

  @Test
  fun `isSystemFrame returns false for app packages`() {
    assertEquals(false, AnrCulpritIdentifier.isSystemFrame("com.example.MyClass"))
    assertEquals(false, AnrCulpritIdentifier.isSystemFrame("io.sentry.samples.MainActivity"))
    assertEquals(false, AnrCulpritIdentifier.isSystemFrame("org.myapp.Feature"))
  }
}
