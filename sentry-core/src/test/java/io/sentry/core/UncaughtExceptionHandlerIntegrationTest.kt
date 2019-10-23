package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import kotlin.test.Test

class UncaughtExceptionHandlerIntegrationTest {
    @Test
    fun `when UncaughtExceptionHandlerIntegration is initialized, uncaught handler is unchanged`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        UncaughtExceptionHandlerIntegration(handlerMock)
        verifyZeroInteractions(handlerMock)
    }

    @Test
    fun `when uncaughtException is called, sentry captures exception`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val hubMock = mock<IHub>()
        val options = SentryOptions()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(hubMock).captureException(throwableMock)
    }

    @Test
    fun `when register is called, current handler is not lost`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val defaultHandlerMock = mock<Thread.UncaughtExceptionHandler>()
        whenever(handlerMock.defaultUncaughtExceptionHandler).thenReturn(defaultHandlerMock)
        val hubMock = mock<IHub>()
        val options = SentryOptions()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(defaultHandlerMock).uncaughtException(threadMock, throwableMock)
    }
}
