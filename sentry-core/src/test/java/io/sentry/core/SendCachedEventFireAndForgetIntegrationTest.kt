package io.sentry.core

import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import kotlin.test.Test

class SendCachedEventFireAndForgetIntegrationTest {
    private class Fixture {
        var hub: IHub? = mock()
        var logger: ILogger? = mock()
        var options = SentryOptions()

        init {
            options.isDebug = true
            options.setLogger(logger)
        }

        fun getSut(): SendCachedEventFireAndForgetIntegration {
            return SendCachedEventFireAndForgetIntegration()
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when cacheDirPath returns null, register logs and exists`() {
        fixture.options.cacheDirPath = null
        val sut = fixture.getSut()
        sut.register(fixture.hub!!, fixture.options)
        verify(fixture.logger)!!.log(eq(SentryLevel.WARNING), eq("No cache dir path is defined in options."))
        verifyNoMoreInteractions(fixture.hub)
    }
}
