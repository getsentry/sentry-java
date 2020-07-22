package io.sentry.core

import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import kotlin.test.Test
import kotlin.test.assertFalse

class SendCachedEventFireAndForgetIntegrationTest {
    private class Fixture {
        var hub: IHub = mock()
        var logger: ILogger = mock()
        var options = SentryOptions()
        var callback = mock<SendCachedEventFireAndForgetIntegration.SendFireAndForgetFactory>()

        init {
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): SendCachedEventFireAndForgetIntegration {
            return SendCachedEventFireAndForgetIntegration(callback)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when cacheDirPath returns null, register logs and exit`() {
        fixture.options.cacheDirPath = null
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), eq("No cache dir path is defined in options."))
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `path is invalid if it is null`() {
        val sut = fixture.getSut()
        assertFalse(fixture.callback.hasValidPath(null, fixture.logger))
    }

    @Test
    fun `path is invalid if it is empty`() {
        val sut = fixture.getSut()
        assertFalse(fixture.callback.hasValidPath("", fixture.logger))
    }

    @Test
    fun `path is valid if not null or empty`() {
        val sut = fixture.getSut()
        assertFalse(fixture.callback.hasValidPath("cache", fixture.logger))
    }

    @Test
    fun `when Factory returns null, register logs and exit`() {
        val sut = SendCachedEventFireAndForgetIntegration(CustomFactory())
        fixture.options.cacheDirPath = "abc"
        sut.register(fixture.hub, fixture.options)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), eq("SendFireAndForget factory is null."))
        verifyNoMoreInteractions(fixture.hub)
    }

    private class CustomFactory : SendCachedEventFireAndForgetIntegration.SendFireAndForgetFactory {
        override fun create(hub: IHub?, options: SentryOptions?): SendCachedEventFireAndForgetIntegration.SendFireAndForget? {
            return null
        }
    }
}
