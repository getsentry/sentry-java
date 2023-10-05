package io.sentry

import io.sentry.exception.ExceptionMechanismException
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.EventDropReason.MULTITHREADED_DEDUPLICATION
import io.sentry.protocol.SentryId
import io.sentry.util.HintUtils
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UncaughtExceptionHandlerIntegrationTest {

    class Fixture {
        val file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
        internal val handler = mock<UncaughtExceptionHandler>()
        val defaultHandler = mock<Thread.UncaughtExceptionHandler>()
        val thread = mock<Thread>()
        val throwable = Throwable("test")
        val hub = mock<IHub>()
        val options = SentryOptions()
        val logger = mock<ILogger>()

        fun getSut(
            flushTimeoutMillis: Long = 0L,
            hasDefaultHandler: Boolean = false,
            enableUncaughtExceptionHandler: Boolean = true,
            isPrintUncaughtStackTrace: Boolean = false
        ): UncaughtExceptionHandlerIntegration {
            options.flushTimeoutMillis = flushTimeoutMillis
            options.isEnableUncaughtExceptionHandler = enableUncaughtExceptionHandler
            options.isPrintUncaughtStackTrace = isPrintUncaughtStackTrace
            options.setLogger(logger)
            options.isDebug = true
            whenever(handler.defaultUncaughtExceptionHandler).thenReturn(if (hasDefaultHandler) defaultHandler else null)
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

        sut.register(fixture.hub, fixture.options)
        sut.uncaughtException(fixture.thread, fixture.throwable)

        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `when register is called, current handler is not lost`() {
        val sut = fixture.getSut(hasDefaultHandler = true, isPrintUncaughtStackTrace = false)

        sut.register(fixture.hub, fixture.options)
        sut.uncaughtException(fixture.thread, fixture.throwable)

        verify(fixture.defaultHandler).uncaughtException(fixture.thread, fixture.throwable)
    }

    @Test
    fun `when uncaughtException is called, exception captured has handled=false`() {
        whenever(fixture.hub.captureException(any())).thenAnswer { invocation ->
            val e = invocation.getArgument<ExceptionMechanismException>(1)
            assertNotNull(e)
            assertNotNull(e.exceptionMechanism)
            assertTrue(e.exceptionMechanism.isHandled!!)
            SentryId.EMPTY_ID
        }

        val sut = fixture.getSut(isPrintUncaughtStackTrace = false)

        sut.register(fixture.hub, fixture.options)
        sut.uncaughtException(fixture.thread, fixture.throwable)

        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `when hub is closed, integrations should be closed`() {
        val integrationMock = mock<UncaughtExceptionHandlerIntegration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.addIntegration(integrationMock)
        options.cacheDirPath = fixture.file.absolutePath
        options.setSerializer(mock())
        val hub = Hub(options)
        hub.close()
        verify(integrationMock).close()
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is disabled, should not install Sentry UncaughtExceptionHandler`() {
        val sut = fixture.getSut(
            enableUncaughtExceptionHandler = false,
            isPrintUncaughtStackTrace = false
        )

        sut.register(fixture.hub, fixture.options)

        verify(fixture.handler, never()).defaultUncaughtExceptionHandler = any()
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is enabled, should install Sentry UncaughtExceptionHandler`() {
        val sut = fixture.getSut(isPrintUncaughtStackTrace = false)

        sut.register(fixture.hub, fixture.options)

        verify(fixture.handler).defaultUncaughtExceptionHandler =
            argWhere { it is UncaughtExceptionHandlerIntegration }
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is set and integration is closed, default uncaught exception handler is reset to previous handler`() {
        val sut = fixture.getSut(hasDefaultHandler = true, isPrintUncaughtStackTrace = false)

        sut.register(fixture.hub, fixture.options)
        whenever(fixture.handler.defaultUncaughtExceptionHandler)
            .thenReturn(sut)
        sut.close()

        verify(fixture.handler).defaultUncaughtExceptionHandler = fixture.defaultHandler
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is not set and integration is closed, default uncaught exception handler is reset to null`() {
        val sut = fixture.getSut(isPrintUncaughtStackTrace = false)

        sut.register(fixture.hub, fixture.options)
        whenever(fixture.handler.defaultUncaughtExceptionHandler)
            .thenReturn(sut)
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

            sut.register(fixture.hub, fixture.options)
            sut.uncaughtException(fixture.thread, RuntimeException("This should be printed!"))

            assertTrue(
                outputStreamCaptor.toString()
                    .contains("java.lang.RuntimeException: This should be printed!")
            )
            assertTrue(
                outputStreamCaptor.toString()
                    .contains("UncaughtExceptionHandlerIntegrationTest.kt:")
            )
        } finally {
            System.setErr(standardErr)
        }
    }

    @Test
    fun `waits for event to flush on disk`() {
        val capturedEventId = SentryId()

        whenever(fixture.hub.captureEvent(any(), any<Hint>())).thenAnswer { invocation ->
            val hint = HintUtils.getSentrySdkHint(invocation.getArgument(1))
                as DiskFlushNotification
            thread {
                Thread.sleep(1000L)
                hint.markFlushed()
            }
            capturedEventId
        }

        val sut = fixture.getSut(flushTimeoutMillis = 5000)

        sut.register(fixture.hub, fixture.options)
        sut.uncaughtException(fixture.thread, fixture.throwable)

        verify(fixture.hub).captureEvent(any(), any<Hint>())
        // shouldn't fall into timed out state, because we marked event as flushed on another thread
        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out") },
            any<Any>()
        )
    }

    @Test
    fun `does not block flushing when the event was dropped`() {
        whenever(fixture.hub.captureEvent(any(), any<Hint>())).thenReturn(SentryId.EMPTY_ID)

        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        sut.uncaughtException(fixture.thread, fixture.throwable)

        verify(fixture.hub).captureEvent(any(), any<Hint>())
        // we do not call markFlushed, hence it should time out waiting for flush, but because
        // we drop the event, it should not even come to this if-check
        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out") },
            any<Any>()
        )
    }

    @Test
    fun `waits for event to flush on disk if it was dropped by multithreaded deduplicator`() {
        val hintCaptor = argumentCaptor<Hint>()
        whenever(fixture.hub.captureEvent(any(), hintCaptor.capture())).thenAnswer {
            HintUtils.setEventDropReason(hintCaptor.firstValue, MULTITHREADED_DEDUPLICATION)
            return@thenAnswer SentryId.EMPTY_ID
        }

        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        sut.uncaughtException(fixture.thread, fixture.throwable)

        verify(fixture.hub).captureEvent(any(), any<Hint>())
        // we do not call markFlushed, even though we dropped the event, the reason was
        // MULTITHREADED_DEDUPLICATION, so it should time out
        verify(fixture.logger).log(
            any(),
            argThat { startsWith("Timed out") },
            any<Any>()
        )
    }
}
