package io.sentry

import java.lang.Exception
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryStackTraceFactoryTest {
  private val sut = SentryStackTraceFactory(SentryOptions())

  @Test
  fun `when getStackFrames is called passing a valid Array, not empty result`() {
    val stacktrace = Thread.currentThread().stackTrace
    // count the stack traces but ignores the test class which is io.sentry package
    val count = stacktrace.size - 1
    assertEquals(count, sut.getStackFrames(stacktrace, false)!!.count())
  }

  @Test
  fun `when line number is negative, not added to sentry stacktrace`() {
    val stacktrace = StackTraceElement("class", "method", "fileName", -2)
    val actual = sut.getStackFrames(arrayOf(stacktrace), false)
    assertNull(actual!![0].lineno)
  }

  @Test
  fun `when line number is positive, gets added to sentry stacktrace`() {
    val stacktrace = StackTraceElement("class", "method", "fileName", 1)
    val actual = sut.getStackFrames(arrayOf(stacktrace), false)
    assertEquals(stacktrace.lineNumber, actual!![0].lineno)
  }

  @Test
  fun `when getStackFrames is called passing empty elements, return null`() {
    assertNull(sut.getStackFrames(arrayOf(), false))
  }

  @Test
  fun `when getStackFrames is called passing null, return null`() {
    assertNull(sut.getStackFrames(null, false))
  }

  @Test
  fun `when getStackFrames is called passing a valid array, fields should be set`() {
    val element = generateStackTrace("class")
    val stacktrace = arrayOf(element)
    val stackFrames = sut.getStackFrames(stacktrace, false)
    assertEquals("class", stackFrames!![0].module)
    assertEquals("method", stackFrames[0].function)
    assertEquals("fileName", stackFrames[0].filename)
    assertEquals(10, stackFrames[0].lineno)
    assertEquals(false, stackFrames[0].isNative)
  }

  // region inAppExcludes
  @Test
  fun `when getStackFrames is called passing a valid inAppExcludes, inApp should be false if prefix matches it`() {
    val element = generateStackTrace("io.mysentry.MyActivity")
    val elements = arrayOf(element)
    val sentryStackTraceFactory =
      SentryStackTraceFactory(SentryOptions().apply { addInAppExclude("io.mysentry") })
    val sentryElements = sentryStackTraceFactory.getStackFrames(elements, false)

    assertFalse(sentryElements!!.first().isInApp!!)
  }

  @Test
  fun `when getStackFrames is called passing a valid inAppExcludes, inApp should be undecided if prefix doesnt matches it`() {
    val element = generateStackTrace("io.myapp.MyActivity")
    val elements = arrayOf(element)
    val sentryStackTraceFactory =
      SentryStackTraceFactory(SentryOptions().apply { addInAppExclude("io.mysentry") })
    val sentryElements = sentryStackTraceFactory.getStackFrames(elements, false)

    assertNull(sentryElements!!.first().isInApp)
  }

  @Test
  fun `when getStackFrames is called passing an invalid inAppExcludes, inApp should undecided`() {
    val element = generateStackTrace("io.mysentry.MyActivity")
    val elements = arrayOf(element)
    val sentryStackTraceFactory = SentryStackTraceFactory(SentryOptions())
    val sentryElements = sentryStackTraceFactory.getStackFrames(elements, false)

    assertNull(sentryElements!!.first().isInApp)
  }

  // endregion

  // region inAppIncludes
  @Test
  fun `when getStackFrames is called passing a valid inAppIncludes, inApp should be true if prefix matches it`() {
    val element = generateStackTrace("io.mysentry.MyActivity")
    val elements = arrayOf(element)
    val sentryStackTraceFactory =
      SentryStackTraceFactory(SentryOptions().apply { addInAppInclude("io.mysentry") })
    val sentryElements = sentryStackTraceFactory.getStackFrames(elements, false)

    assertTrue(sentryElements!!.first().isInApp!!)
  }

  @Test
  fun `when getStackFrames is called passing a valid inAppIncludes, inApp should be undecided if prefix doesnt matches it`() {
    val element = generateStackTrace("io.myapp.MyActivity")
    val elements = arrayOf(element)
    val sentryStackTraceFactory =
      SentryStackTraceFactory(SentryOptions().apply { addInAppInclude("io.mysentry") })
    val sentryElements = sentryStackTraceFactory.getStackFrames(elements, false)

    assertNull(sentryElements!!.first().isInApp)
  }

  @Test
  fun `when getStackFrames is called passing an invalid inAppIncludes, inApp should be undecided`() {
    val element = generateStackTrace("io.mysentry.MyActivity")
    val elements = arrayOf(element)
    val sentryStackTraceFactory = SentryStackTraceFactory(SentryOptions())
    val sentryElements = sentryStackTraceFactory.getStackFrames(elements, false)

    assertNull(sentryElements!!.first().isInApp)
  }

  // endregion

  @Test
  fun `when getStackFrames is called passing a valid inAppIncludes and inAppExcludes, inApp should take precedence`() {
    val element = generateStackTrace("io.mysentry.MyActivity")
    val elements = arrayOf(element)
    val sentryStackTraceFactory =
      SentryStackTraceFactory(
        SentryOptions().apply {
          addInAppExclude("io.mysentry")
          addInAppInclude("io.mysentry")
        }
      )
    val sentryElements = sentryStackTraceFactory.getStackFrames(elements, false)

    assertTrue(sentryElements!!.first().isInApp!!)
  }

  @Test
  fun `when class is defined in the app, inApp is true`() {
    val sentryStackTraceFactory =
      SentryStackTraceFactory(
        SentryOptions().apply {
          addInAppExclude("io.mysentry.not")
          addInAppInclude("io.mysentry.inApp")
        }
      )
    assertTrue(sentryStackTraceFactory.isInApp("io.mysentry.inApp.ClassName")!!)
    assertTrue(sentryStackTraceFactory.isInApp("io.mysentry.inApp.somePackage.ClassName")!!)
    assertFalse(sentryStackTraceFactory.isInApp("io.mysentry.not.ClassName")!!)
    assertFalse(sentryStackTraceFactory.isInApp("io.mysentry.not.somePackage.ClassName")!!)
  }

  @Test
  fun `when class is not in the list, is left undecided`() {
    val sentryStackTraceFactory =
      SentryStackTraceFactory(SentryOptions().apply { addInAppInclude("io.mysentry") })
    assertNull(sentryStackTraceFactory.isInApp("com.getsentry"))
  }

  @Test
  fun `when getStackFrames is called, remove sentry classes`() {
    var stacktrace = Thread.currentThread().stackTrace
    val sentryElement = StackTraceElement("io.sentry.element", "test", "test.java", 1)
    stacktrace = stacktrace.plusElement(sentryElement)

    assertNull(
      sut.getStackFrames(stacktrace, false)!!.find {
        it.module != null && it.module!!.startsWith("io.sentry")
      }
    )
  }

  @Test
  fun `when getStackFrames is called with includeSentryFrames, does not remove sentry classes`() {
    var stacktrace = Thread.currentThread().stackTrace
    val sentryElement = StackTraceElement("io.sentry.element", "test", "test.java", 1)
    stacktrace = stacktrace.plusElement(sentryElement)

    assertTrue(
      sut.getStackFrames(stacktrace, true)!!.any {
        it.module != null && it.module!!.startsWith("io.sentry")
      }
    )
  }

  @Test
  fun `when getStackFrames is called, does not remove sentry samples classes`() {
    var stacktrace = Thread.currentThread().stackTrace
    val sentryElement = StackTraceElement("io.sentry.samples.element", "test", "test.java", 1)
    stacktrace = stacktrace.plusElement(sentryElement)

    assertNotNull(
      sut.getStackFrames(stacktrace, false)!!.find {
        it.module != null && it.module!!.startsWith("io.sentry")
      }
    )
  }

  @Test
  fun `when getStackFrames is called, does not remove sentry mobile classes`() {
    var stacktrace = Thread.currentThread().stackTrace
    val sentryElement = StackTraceElement("io.sentry.mobile.element", "test", "test.java", 1)
    stacktrace = stacktrace.plusElement(sentryElement)

    assertNotNull(
      sut.getStackFrames(stacktrace, false)!!.find {
        it.module != null && it.module!!.startsWith("io.sentry")
      }
    )
  }

  @Test
  fun `when stacktrace is not available, returns empty list for call stack`() {
    val exception = Exception()
    exception.stackTrace = arrayOf<StackTraceElement>()
    val sut = SentryStackTraceFactory(SentryOptions().apply { addInAppInclude("io.mysentry") })

    val callStack = sut.getInAppCallStack(exception)

    assertEquals(0, callStack.size)
  }

  @Test
  fun `excludes sentry frames from the call stack`() {
    val exception = Exception()
    exception.stackTrace =
      arrayOf(
        generateStackTrace("io.sentry.instrumentation.file.FileIOSpanManager"),
        generateStackTrace("io.sentry.instrumentation.file.SentryFileOutputStream"),
        generateStackTrace("com.example.myapp.MainActivity"),
      )
    val sut = SentryStackTraceFactory(SentryOptions().apply { addInAppInclude("io.mysentry") })

    val callStack = sut.getInAppCallStack(exception)

    assertEquals(1, callStack.size)
    assertEquals("com.example.myapp.MainActivity", callStack[0].module)
  }

  @Test
  fun `includes only in-app frames to the call stack`() {
    val exception = Exception()
    exception.stackTrace =
      arrayOf(
        generateStackTrace("io.sentry.instrumentation.file.FileIOSpanManager"),
        generateStackTrace("io.sentry.instrumentation.file.SentryFileOutputStream"),
        generateStackTrace("com.example.myapp.MainActivity"),
        generateStackTrace("com.thirdparty.Adapter"),
      )
    val sut = SentryStackTraceFactory(SentryOptions().apply { addInAppInclude("com.example") })

    val callStack = sut.getInAppCallStack(exception)

    assertEquals(1, callStack.size)
    assertEquals("com.example.myapp.MainActivity", callStack[0].module)
  }

  @Test
  fun `when inAppIncludes are not provided, excludes at least system frames`() {
    val exception = Exception()
    exception.stackTrace =
      arrayOf(
        generateStackTrace("io.sentry.instrumentation.file.FileIOSpanManager"),
        generateStackTrace("io.sentry.instrumentation.file.SentryFileOutputStream"),
        generateStackTrace("com.example.myapp.MainActivity"),
        generateStackTrace("com.thirdparty.Adapter"),
        generateStackTrace("sun.misc.unsafe.park.Object"),
        generateStackTrace("java.lang.Object"),
      )
    val sut = SentryStackTraceFactory(SentryOptions())

    val callStack = sut.getInAppCallStack(exception)

    assertEquals(2, callStack.size)
    // underlying getStackFrames reverses the order
    assertEquals("com.thirdparty.Adapter", callStack[0].module)
    assertEquals("com.example.myapp.MainActivity", callStack[1].module)
  }

  @Test
  fun `caps number of stack frames to STACKTRACE_FRAME_LIMIT to not exceed payload size limit`() {
    val exception = Exception()
    exception.stackTrace = arrayOf()
    repeat(120) { exception.stackTrace += generateStackTrace("com.me.stackoverflow") }

    val sut = SentryStackTraceFactory(SentryOptions())
    val sentryFrames = sut.getStackFrames(exception.stackTrace, false)

    assertEquals(100, sentryFrames!!.size)
  }

  private fun generateStackTrace(className: String?) =
    StackTraceElement(className, "method", "fileName", 10)
}
