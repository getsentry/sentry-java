package io.sentry

import io.sentry.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.sentry.exception.ExceptionMechanismException
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.EventDropReason.MULTITHREADED_DEDUPLICATION
import io.sentry.protocol.SentryId
import io.sentry.test.createTestScopes
import io.sentry.util.HintUtils
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UncaughtExceptionHandlerIntegrationTest {

  class Fixture {
    val file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    internal val handler = mock<UncaughtExceptionHandler>()
    val defaultHandler = mock<Thread.UncaughtExceptionHandler>()
    val thread = mock<Thread>()
    val throwable = Throwable("test")
    val scopes = mock<IScopes>()
    val options = SentryOptions()
    val logger = mock<ILogger>()

    fun getSut(
      flushTimeoutMillis: Long = 0L,
      hasDefaultHandler: Boolean = false,
      enableUncaughtExceptionHandler: Boolean = true,
      isPrintUncaughtStackTrace: Boolean = false,
    ): UncaughtExceptionHandlerIntegration {
      options.flushTimeoutMillis = flushTimeoutMillis
      options.isEnableUncaughtExceptionHandler = enableUncaughtExceptionHandler
      options.isPrintUncaughtStackTrace = isPrintUncaughtStackTrace
      options.setLogger(logger)
      options.isDebug = true
      whenever(handler.defaultUncaughtExceptionHandler)
        .thenReturn(if (hasDefaultHandler) defaultHandler else null)
      return UncaughtExceptionHandlerIntegration(handler)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when UncaughtExceptionHandlerIntegration is initialized, uncaught handler is unchanged`() {
    fixture.getSut(isPrintUncaughtStackTrace = false)

    verify(fixture.handler, never()).defaultUncaughtExceptionHandler = any()
  }

  @Test
  fun `when uncaughtException is called, sentry captures exception`() {
    val sut = fixture.getSut(isPrintUncaughtStackTrace = false)

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `when register is called, current handler is not lost`() {
    val sut = fixture.getSut(hasDefaultHandler = true, isPrintUncaughtStackTrace = false)

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.defaultHandler).uncaughtException(fixture.thread, fixture.throwable)
  }

  @Test
  fun `when uncaughtException is called, exception captured has handled=false`() {
    whenever(fixture.scopes.captureException(any())).thenAnswer { invocation ->
      val e = invocation.getArgument<ExceptionMechanismException>(1)
      assertNotNull(e)
      assertNotNull(e.exceptionMechanism)
      assertTrue(e.exceptionMechanism.isHandled!!)
      SentryId.EMPTY_ID
    }

    val sut = fixture.getSut(isPrintUncaughtStackTrace = false)

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `when scopes is closed, integrations should be closed`() {
    val integrationMock = mock<UncaughtExceptionHandlerIntegration>()
    val options = SentryOptions()
    options.dsn = "https://key@sentry.io/proj"
    options.addIntegration(integrationMock)
    options.cacheDirPath = fixture.file.absolutePath
    options.setSerializer(mock())
    val scopes = createTestScopes(options)
    scopes.close()
    verify(integrationMock).close()
  }

  @Test
  fun `When defaultUncaughtExceptionHandler is disabled, should not install Sentry UncaughtExceptionHandler`() {
    val sut =
      fixture.getSut(enableUncaughtExceptionHandler = false, isPrintUncaughtStackTrace = false)

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.handler, never()).defaultUncaughtExceptionHandler = any()
  }

  @Test
  fun `When defaultUncaughtExceptionHandler is enabled, should install Sentry UncaughtExceptionHandler`() {
    val sut = fixture.getSut(isPrintUncaughtStackTrace = false)

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.handler).defaultUncaughtExceptionHandler = argWhere {
      it is UncaughtExceptionHandlerIntegration
    }
  }

  @Test
  fun `When defaultUncaughtExceptionHandler is set and integration is closed, default uncaught exception handler is reset to previous handler`() {
    val sut = fixture.getSut(hasDefaultHandler = true, isPrintUncaughtStackTrace = false)

    sut.register(fixture.scopes, fixture.options)
    whenever(fixture.handler.defaultUncaughtExceptionHandler).thenReturn(sut)
    sut.close()

    verify(fixture.handler).defaultUncaughtExceptionHandler = fixture.defaultHandler
  }

  @Test
  fun `When defaultUncaughtExceptionHandler is not set and integration is closed, default uncaught exception handler is reset to null`() {
    val sut = fixture.getSut(isPrintUncaughtStackTrace = false)

    sut.register(fixture.scopes, fixture.options)
    whenever(fixture.handler.defaultUncaughtExceptionHandler).thenReturn(sut)
    sut.close()

    verify(fixture.handler).defaultUncaughtExceptionHandler = null
  }

  @Test
  fun `When printUncaughtStackTrace is enabled, prints the stacktrace to standard error`() {
    val standardErr = System.err
    try {
      val outputStreamCaptor = ByteArrayOutputStream()
      System.setErr(PrintStream(outputStreamCaptor))

      val sut = fixture.getSut(isPrintUncaughtStackTrace = true)

      sut.register(fixture.scopes, fixture.options)
      sut.uncaughtException(fixture.thread, RuntimeException("This should be printed!"))

      assertTrue(
        outputStreamCaptor
          .toString()
          .contains("java.lang.RuntimeException: This should be printed!")
      )
      assertTrue(
        outputStreamCaptor.toString().contains("UncaughtExceptionHandlerIntegrationTest.kt:")
      )
    } finally {
      System.setErr(standardErr)
    }
  }

  @Test
  fun `waits for event to flush on disk`() {
    val capturedEventId = SentryId()

    whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer { invocation ->
      val hint = HintUtils.getSentrySdkHint(invocation.getArgument(1)) as DiskFlushNotification
      thread {
        Thread.sleep(1000L)
        hint.markFlushed()
      }
      capturedEventId
    }

    val sut = fixture.getSut(flushTimeoutMillis = 5000)

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
    // shouldn't fall into timed out state, because we marked event as flushed on another thread
    verify(fixture.logger, never()).log(any(), argThat { startsWith("Timed out") }, any<Any>())
  }

  @Test
  fun `does not block flushing when the event was dropped`() {
    whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenReturn(SentryId.EMPTY_ID)

    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
    // we do not call markFlushed, hence it should time out waiting for flush, but because
    // we drop the event, it should not even come to this if-check
    verify(fixture.logger, never()).log(any(), argThat { startsWith("Timed out") }, any<Any>())
  }

  @Test
  fun `waits for event to flush on disk if it was dropped by multithreaded deduplicator`() {
    val hintCaptor = argumentCaptor<Hint>()
    whenever(fixture.scopes.captureEvent(any(), hintCaptor.capture())).thenAnswer {
      HintUtils.setEventDropReason(hintCaptor.firstValue, MULTITHREADED_DEDUPLICATION)
      return@thenAnswer SentryId.EMPTY_ID
    }

    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
    // we do not call markFlushed, even though we dropped the event, the reason was
    // MULTITHREADED_DEDUPLICATION, so it should time out
    verify(fixture.logger).log(any(), argThat { startsWith("Timed out") }, any<Any>())
  }

  @Test
  fun `when there is no active transaction on scope, sets current event id as flushable`() {
    val eventCaptor = argumentCaptor<SentryEvent>()
    whenever(fixture.scopes.captureEvent(eventCaptor.capture(), any<Hint>()))
      .thenReturn(SentryId.EMPTY_ID)

    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.scopes)
      .captureEvent(
        any(),
        argThat<Hint> {
          (HintUtils.getSentrySdkHint(this) as UncaughtExceptionHint).isFlushable(
            eventCaptor.firstValue.eventId
          )
        },
      )
  }

  @Test
  fun `when there is active transaction on scope, does not set current event id as flushable`() {
    val eventCaptor = argumentCaptor<SentryEvent>()
    whenever(fixture.scopes.transaction).thenReturn(mock<ITransaction>())
    whenever(fixture.scopes.captureEvent(eventCaptor.capture(), any<Hint>()))
      .thenReturn(SentryId.EMPTY_ID)

    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    sut.uncaughtException(fixture.thread, fixture.throwable)

    verify(fixture.scopes)
      .captureEvent(
        any(),
        argThat<Hint> {
          !(HintUtils.getSentrySdkHint(this) as UncaughtExceptionHint).isFlushable(
            eventCaptor.firstValue.eventId
          )
        },
      )
  }

  @Test
  fun `multiple registrations do not cause the build-up of a tree of UncaughtExceptionHandlerIntegrations`() {
    var currentDefaultHandler: Thread.UncaughtExceptionHandler? = null

    val handler = mock<UncaughtExceptionHandler>()
    whenever(handler.defaultUncaughtExceptionHandler).thenAnswer { currentDefaultHandler }

    whenever(
        handler.setDefaultUncaughtExceptionHandler(anyOrNull<Thread.UncaughtExceptionHandler>())
      )
      .then {
        currentDefaultHandler = it.getArgument(0)
        null
      }

    val integration1 = UncaughtExceptionHandlerIntegration(handler)
    integration1.register(fixture.scopes, fixture.options)

    val integration2 = UncaughtExceptionHandlerIntegration(handler)
    integration2.register(fixture.scopes, fixture.options)

    assertEquals(integration2, currentDefaultHandler)
    integration2.close()

    assertEquals(null, currentDefaultHandler)
  }

  @Test
  fun `multiple registrations do not cause the build-up of a tree of UncaughtExceptionHandlerIntegrations, reset to inital`() {
    val initialUncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }

    var currentDefaultHandler: Thread.UncaughtExceptionHandler? = initialUncaughtExceptionHandler

    val handler = mock<UncaughtExceptionHandler>()
    whenever(handler.defaultUncaughtExceptionHandler).thenAnswer { currentDefaultHandler }

    whenever(
        handler.setDefaultUncaughtExceptionHandler(anyOrNull<Thread.UncaughtExceptionHandler>())
      )
      .then {
        currentDefaultHandler = it.getArgument(0)
        null
      }

    val integration1 = UncaughtExceptionHandlerIntegration(handler)
    integration1.register(fixture.scopes, fixture.options)

    val integration2 = UncaughtExceptionHandlerIntegration(handler)
    integration2.register(fixture.scopes, fixture.options)

    assertEquals(currentDefaultHandler, integration2)
    integration2.close()

    assertEquals(initialUncaughtExceptionHandler, currentDefaultHandler)
  }

  @Test
  fun `multiple registrations with different global scopes allowed`() {
    val scopes2 = mock<IScopes>()
    val initialUncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }

    var currentDefaultHandler: Thread.UncaughtExceptionHandler? = initialUncaughtExceptionHandler

    val handler = mock<UncaughtExceptionHandler>()
    whenever(handler.defaultUncaughtExceptionHandler).thenAnswer { currentDefaultHandler }

    whenever(
        handler.setDefaultUncaughtExceptionHandler(anyOrNull<Thread.UncaughtExceptionHandler>())
      )
      .then {
        currentDefaultHandler = it.getArgument(0)
        null
      }

    whenever(scopes2.globalScope).thenReturn(mock<IScope>())

    val integration1 = UncaughtExceptionHandlerIntegration(handler)
    integration1.register(fixture.scopes, fixture.options)

    val integration2 = UncaughtExceptionHandlerIntegration(handler)
    integration2.register(scopes2, fixture.options)

    assertEquals(currentDefaultHandler, integration2)
    integration2.close()

    assertEquals(integration1, currentDefaultHandler)
    integration1.close()

    assertEquals(initialUncaughtExceptionHandler, currentDefaultHandler)
  }

  @Test
  fun `multiple registrations with different global scopes allowed, closed out of order`() {
    fixture.getSut()
    val scopes2 = mock<IScopes>()
    val initialUncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }

    var currentDefaultHandler: Thread.UncaughtExceptionHandler? = initialUncaughtExceptionHandler

    val handler = mock<UncaughtExceptionHandler>()
    whenever(handler.defaultUncaughtExceptionHandler).thenAnswer { currentDefaultHandler }

    whenever(
        handler.setDefaultUncaughtExceptionHandler(anyOrNull<Thread.UncaughtExceptionHandler>())
      )
      .then {
        currentDefaultHandler = it.getArgument(0)
        null
      }

    whenever(scopes2.globalScope).thenReturn(mock<IScope>())

    val integration1 = UncaughtExceptionHandlerIntegration(handler)
    integration1.register(fixture.scopes, fixture.options)

    val integration2 = UncaughtExceptionHandlerIntegration(handler)
    integration2.register(scopes2, fixture.options)

    assertEquals(currentDefaultHandler, integration2)
    integration1.close()

    assertEquals(integration2, currentDefaultHandler)
    integration2.close()

    assertEquals(initialUncaughtExceptionHandler, currentDefaultHandler)
  }

  @Test
  fun `multiple registrations async, closed async, one remains`() {
    val executor = Executors.newFixedThreadPool(4)
    fixture.getSut()
    val scopes2 = mock<IScopes>()
    val scopes3 = mock<IScopes>()
    val scopes4 = mock<IScopes>()
    val scopes5 = mock<IScopes>()

    val scopesList = listOf(fixture.scopes, scopes2, scopes3, scopes4, scopes5)

    val initialUncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }

    var currentDefaultHandler: Thread.UncaughtExceptionHandler? = initialUncaughtExceptionHandler

    val handler = mock<UncaughtExceptionHandler>()
    whenever(handler.defaultUncaughtExceptionHandler).thenAnswer { currentDefaultHandler }

    whenever(
        handler.setDefaultUncaughtExceptionHandler(anyOrNull<Thread.UncaughtExceptionHandler>())
      )
      .then {
        currentDefaultHandler = it.getArgument(0)
        null
      }

    whenever(scopes2.globalScope).thenReturn(mock<IScope>())
    whenever(scopes3.globalScope).thenReturn(mock<IScope>())
    whenever(scopes4.globalScope).thenReturn(mock<IScope>())
    whenever(scopes5.globalScope).thenReturn(mock<IScope>())

    val integrations =
      scopesList.map { scope ->
        CompletableFuture.supplyAsync(
          {
            UncaughtExceptionHandlerIntegration(handler).apply { register(scope, fixture.options) }
          },
          executor,
        )
      }

    CompletableFuture.allOf(*integrations.toTypedArray()).get()

    val futures =
      integrations.minus(integrations[2]).reversed().map { integration ->
        CompletableFuture.supplyAsync(
          {
            integration.get().close()
            println(Thread.currentThread().name)
          },
          executor,
        )
      }

    CompletableFuture.allOf(*futures.toTypedArray()).get()

    assertEquals(integrations[2].get(), currentDefaultHandler)
  }

  @Test
  fun `removeFromHandlerTree detects and handles cyclic dependencies`() {
    var currentDefaultHandler: Thread.UncaughtExceptionHandler? = null
    val scopes2 = mock<IScopes>()
    val scopes3 = mock<IScopes>()
    val scopes4 = mock<IScopes>()

    whenever(scopes2.globalScope).thenReturn(mock<IScope>())
    whenever(scopes3.globalScope).thenReturn(mock<IScope>())
    whenever(scopes4.globalScope).thenReturn(mock<IScope>())

    val handler = mock<UncaughtExceptionHandler>()
    whenever(handler.defaultUncaughtExceptionHandler).thenAnswer { currentDefaultHandler }

    whenever(
        handler.setDefaultUncaughtExceptionHandler(anyOrNull<Thread.UncaughtExceptionHandler>())
      )
      .then {
        currentDefaultHandler = it.getArgument(0)
        null
      }

    val logger = mock<ILogger>()
    val options =
      SentryOptions().apply {
        setLogger(logger)
        isDebug = true
      }

    val handlerA = UncaughtExceptionHandlerIntegration(handler)
    val handlerB = UncaughtExceptionHandlerIntegration(handler)
    handlerA.register(fixture.scopes, options)
    handlerB.register(scopes2, options)

    // Cycle: A → B → A
    val defaultHandlerField =
      UncaughtExceptionHandlerIntegration::class.java.getDeclaredField("defaultExceptionHandler")
    defaultHandlerField.isAccessible = true
    defaultHandlerField.set(handlerA, handlerB)
    defaultHandlerField.set(handlerB, handlerA)

    // Register handlerC to be removed from the chain
    val handlerC = UncaughtExceptionHandlerIntegration(handler)
    handlerC.register(scopes3, options)

    assertEquals(handlerC, currentDefaultHandler)

    // Register handlerD to be the current default
    // Same Scope as handlerC so that removing handlerC would trigger a cycle
    val handlerD = UncaughtExceptionHandlerIntegration(handler)
    handlerD.register(scopes3, options)

    assertEquals(handlerD, currentDefaultHandler)

    handlerC.close()

    // Verify cycle detection warning was logged
    verify(logger, atLeastOnce())
      .log(
        SentryLevel.WARNING,
        "Cycle detected in UncaughtExceptionHandler chain while removing handler.",
      )
  }
}
