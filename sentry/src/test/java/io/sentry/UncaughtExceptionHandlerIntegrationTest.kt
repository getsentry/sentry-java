package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.SentryId
import io.sentry.util.noFlushTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UncaughtExceptionHandlerIntegrationTest {

    private lateinit var file: File

    @BeforeTest
    fun `set up`() {
        file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    }

    @AfterTest
    fun shutdown() {
        Files.delete(file.toPath())
    }

    @Test
    fun `when UncaughtExceptionHandlerIntegration is initialized, uncaught handler is unchanged`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        UncaughtExceptionHandlerIntegration(handlerMock)
        verify(handlerMock, never()).defaultUncaughtExceptionHandler = any()
    }

    @Test
    fun `when uncaughtException is called, sentry captures exception`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val hubMock = mock<IHub>()
        val options = SentryOptions().noFlushTimeout()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(hubMock).captureEvent(any(), any())
    }

    @Test
    fun `when register is called, current handler is not lost`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val defaultHandlerMock = mock<Thread.UncaughtExceptionHandler>()
        whenever(handlerMock.defaultUncaughtExceptionHandler).thenReturn(defaultHandlerMock)
        val hubMock = mock<IHub>()
        val options = SentryOptions().noFlushTimeout()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(defaultHandlerMock).uncaughtException(threadMock, throwableMock)
    }

    @Test
    fun `when uncaughtException is called, exception captured has handled=false`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val hubMock = mock<IHub>()
        whenever(hubMock.captureException(any())).thenAnswer { invocation ->
            val e = (invocation.arguments[1] as ExceptionMechanismException)
            assertNotNull(e)
            assertNotNull(e.exceptionMechanism)
            assertTrue(e.exceptionMechanism.isHandled!!)
            SentryId.EMPTY_ID
        }
        val options = SentryOptions().noFlushTimeout()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(hubMock).captureEvent(any(), any())
    }

    @Test
    fun `when hub is closed, integrations should be closed`() {
        val integrationMock = mock<UncaughtExceptionHandlerIntegration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.addIntegration(integrationMock)
        options.cacheDirPath = file.absolutePath
        options.setSerializer(mock())
//        val expected = HubAdapter.getInstance()
        val hub = Hub(options)
//        verify(integrationMock).register(expected, options)
        hub.close()
        verify(integrationMock).close()
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is disabled, should not install Sentry UncaughtExceptionHandler`() {
        val options = SentryOptions()
        options.enableUncaughtExceptionHandler = false
        val hub = mock<IHub>()
        val handlerMock = mock<UncaughtExceptionHandler>()
        val integration = UncaughtExceptionHandlerIntegration(handlerMock)
        integration.register(hub, options)
        verify(handlerMock, never()).defaultUncaughtExceptionHandler = any()
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is enabled, should install Sentry UncaughtExceptionHandler`() {
        val options = SentryOptions()
        val hub = mock<IHub>()
        val handlerMock = mock<UncaughtExceptionHandler>()
        val integration = UncaughtExceptionHandlerIntegration(handlerMock)
        integration.register(hub, options)
        verify(handlerMock).defaultUncaughtExceptionHandler = argWhere { it is UncaughtExceptionHandlerIntegration }
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is set and integration is closed, default uncaught exception handler is reset to previous handler`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val integration = UncaughtExceptionHandlerIntegration(handlerMock)

        val defaultExceptionHandler = mock<Thread.UncaughtExceptionHandler>()
        whenever(handlerMock.defaultUncaughtExceptionHandler)
            .thenReturn(defaultExceptionHandler)
        integration.register(mock(), SentryOptions())
        whenever(handlerMock.defaultUncaughtExceptionHandler)
            .thenReturn(integration)
        integration.close()
        verify(handlerMock).defaultUncaughtExceptionHandler = defaultExceptionHandler
    }

    @Test
    fun `When defaultUncaughtExceptionHandler is not set and integration is closed, default uncaught exception handler is reset to null`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val integration = UncaughtExceptionHandlerIntegration(handlerMock)

        whenever(handlerMock.defaultUncaughtExceptionHandler)
            .thenReturn(null)
        integration.register(mock(), SentryOptions())
        whenever(handlerMock.defaultUncaughtExceptionHandler)
            .thenReturn(integration)
        integration.close()
        verify(handlerMock).defaultUncaughtExceptionHandler = null
    }

    @Test
    fun `When printUncaughtStackTrace is enabled, prints the stacktrace to standard error`() {
        val standardErr = System.err
        try {
            val outputStreamCaptor = ByteArrayOutputStream()
            System.setErr(PrintStream(outputStreamCaptor))

            val handlerMock = mock<UncaughtExceptionHandler>()
            val options = SentryOptions().noFlushTimeout()
            options.printUncaughtStackTrace = true
            val sut = UncaughtExceptionHandlerIntegration(handlerMock)
            sut.register(mock<IHub>(), options)
            sut.uncaughtException(mock<Thread>(), RuntimeException("This should be printed!"))

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
}
