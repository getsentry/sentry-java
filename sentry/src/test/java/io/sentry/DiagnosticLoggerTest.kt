package io.sentry

import io.sentry.test.callMethod
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class DiagnosticLoggerTest {
  private class Fixture {
    val options =
      SentryOptions().apply {
        setDebug(true)
        setLogger(logger)
      }
    var logger: ILogger? = mock()

    fun getSut(): DiagnosticLogger = DiagnosticLogger(options, logger)
  }

  private val fixture = Fixture()
  private val expectedMessage = "some message"
  private val expectedThrowable = mock<Throwable>()

  @Test
  fun `when default fixture, log recorded`() {
    val sut = fixture.getSut()
    sut.log(SentryLevel.FATAL, "test")
    verify(fixture.logger)?.log(any(), any())
  }

  @Test
  fun `when debug is true and Logger is null, a call to log does not throw`() {
    fixture.logger = null
    fixture.getSut().log(SentryLevel.DEBUG, expectedMessage)
  }

  @Test
  fun `when debug is true and level is set to null, a call to log does not throw`() {
    fixture.options.setDiagnosticLevel(null)
    fixture.getSut().log(SentryLevel.DEBUG, expectedMessage)
  }

  @Test
  fun `when debug is true, a call to log with null level does not throw`() {
    fixture
      .getSut()
      .callMethod(
        "log",
        parameterTypes =
          arrayOf(SentryLevel::class.java, String::class.java, arrayOf<Any>()::class.java),
        null,
        expectedMessage,
        null,
      )
  }

  @Test
  fun `when debug is true, a call to log with null level and throwable does not throw`() {
    fixture
      .getSut()
      .callMethod(
        "log",
        parameterTypes =
          arrayOf(SentryLevel::class.java, String::class.java, Throwable::class.java),
        null,
        expectedMessage,
        expectedThrowable,
      )
  }

  @Test
  fun `when debug is true and Logger is null, a call to log with throwable does not throw`() {
    fixture.logger = null
    fixture.getSut().log(SentryLevel.DEBUG, expectedMessage, expectedThrowable)
  }

  @Test
  fun `when debug is true and option level is info, a call to log and level debug is not logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.INFO)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.DEBUG
    sut.log(expectedLevel, expectedMessage)
    verify(fixture.logger, never())!!.log(expectedLevel, expectedMessage)
  }

  @Test
  fun `when debug is true and option level is error, a call to log and level fatal is logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.ERROR)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.FATAL
    sut.log(expectedLevel, expectedMessage)
    verify(fixture.logger)!!.log(expectedLevel, expectedMessage)
  }

  @Test
  fun `when debug is false and option level is fatal, a call to log and level error is not logged`() {
    fixture.options.setDebug(false)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.FATAL
    sut.log(expectedLevel, expectedMessage)
    verify(fixture.logger, never())!!.log(expectedLevel, expectedMessage)
  }

  @Test
  fun `when debug is true option level is info, a call to log and level debug is not logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.FATAL)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.DEBUG
    sut.log(expectedLevel, expectedMessage)
    verify(fixture.logger, never())!!.log(expectedLevel, expectedMessage)
  }

  @Test
  fun `when debug is true option level is debug, a call to log with throwable and level info is logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.DEBUG)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.INFO
    sut.log(expectedLevel, expectedMessage, expectedThrowable)
    verify(fixture.logger)!!.log(expectedLevel, expectedMessage, expectedThrowable)
  }

  @Test
  fun `when debug is false option level is debug, a call to log with throwable and level info is not logged`() {
    fixture.options.setDebug(false)
    fixture.options.setDiagnosticLevel(SentryLevel.DEBUG)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.INFO
    sut.log(expectedLevel, expectedMessage, expectedThrowable)
    verify(fixture.logger, never())!!.log(expectedLevel, expectedMessage, expectedThrowable)
  }

  @Test
  fun `when debug is true option level is error, a call to log with throwable and level fatal is logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.ERROR)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.FATAL
    sut.log(expectedLevel, expectedMessage, expectedThrowable)
    verify(fixture.logger)!!.log(expectedLevel, expectedMessage, expectedThrowable)
  }

  @Test
  fun `when debug is true option level is error, a call to log with throwable and level error is logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.ERROR)
    val sut = fixture.getSut()
    val expectedLevel = fixture.options.diagnosticLevel
    sut.log(expectedLevel, expectedMessage, expectedThrowable)
    verify(fixture.logger)!!.log(expectedLevel, expectedMessage, expectedThrowable)
  }

  @Test
  fun `when debug is true option level is error, a call to log and level error is logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.ERROR)
    val sut = fixture.getSut()
    val expectedLevel = fixture.options.diagnosticLevel
    sut.log(expectedLevel, expectedMessage)
    verify(fixture.logger)!!.log(expectedLevel, expectedMessage)
  }

  @Test
  fun `when debug is false option level is fatal, a call to log with throwable and level error is not logged`() {
    fixture.options.setDebug(false)
    fixture.options.setDiagnosticLevel(SentryLevel.FATAL)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.ERROR
    sut.log(expectedLevel, expectedMessage, expectedThrowable)
    verify(fixture.logger, never())!!.log(expectedLevel, expectedMessage, expectedThrowable)
  }

  @Test
  fun `when debug is true option level is info, a call to log with throwable and level debug is not logged`() {
    fixture.options.setDiagnosticLevel(SentryLevel.INFO)
    val sut = fixture.getSut()
    val expectedLevel = SentryLevel.DEBUG
    sut.log(expectedLevel, expectedMessage, expectedThrowable)
    verify(fixture.logger, never())!!.log(expectedLevel, expectedMessage, expectedThrowable)
  }
}
