package io.sentry

import io.sentry.protocol.SdkVersion
import io.sentry.test.ImmediateExecutorService
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SendCachedEnvelopeFireAndForgetIntegrationTest {
    private class Fixture {
        var hub: IHub = mock()
        var logger: ILogger = mock()
        var options = SentryOptions()
        var callback = mock<CustomFactory>().apply {
            whenever(hasValidPath(any(), any())).thenCallRealMethod()
        }

        init {
            options.setDebug(true)
            options.setLogger(logger)
            options.sdkVersion = SdkVersion("test", "1.2.3")
        }

        fun getSut(): SendCachedEnvelopeFireAndForgetIntegration {
            return SendCachedEnvelopeFireAndForgetIntegration(callback)
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
        fixture.getSut()
        assertFalse(fixture.callback.hasValidPath(null, fixture.logger))
    }

    @Test
    fun `path is invalid if it is empty`() {
        fixture.getSut()
        assertFalse(fixture.callback.hasValidPath("", fixture.logger))
    }

    @Test
    fun `path is valid if not null or empty`() {
        fixture.getSut()
        assertTrue(fixture.callback.hasValidPath("cache", fixture.logger))
    }

    @Test
    fun `when Factory returns null, register logs and exit`() {
        val sut = SendCachedEnvelopeFireAndForgetIntegration(CustomFactory())
        fixture.options.cacheDirPath = "abc"
        fixture.options.executorService = ImmediateExecutorService()
        sut.register(fixture.hub, fixture.options)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), eq("SendFireAndForget factory is null."))
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `sets SDKVersion Info`() {
        fixture.options.cacheDirPath = "cache"
        whenever(fixture.callback.create(any(), any())).thenReturn(
            mock()
        )
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        assertNotNull(fixture.options.sdkVersion)
        assert(fixture.options.sdkVersion!!.integrationSet.contains("SendCachedEnvelopeFireAndForget"))
    }

    @Test
    fun `register does not throw on executor shut down`() {
        fixture.options.cacheDirPath = "cache"
        fixture.options.executorService.close(0)
        whenever(fixture.callback.create(any(), any())).thenReturn(mock())
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), eq("Failed to call the executor. Cached events will not be sent. Did you call Sentry.close()?"), any())
    }

    @Test
    fun `register runs on executor service`() {
        fixture.options.executorService = ImmediateExecutorService()
        fixture.options.cacheDirPath = "cache"
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        verify(fixture.callback).create(eq(fixture.hub), eq(fixture.options))
    }

    @Test
    fun `does not register on fake executor service`() {
        fixture.options.executorService = mock()
        fixture.options.cacheDirPath = "cache"
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        verify(fixture.callback, never()).create(any(), any())
    }

    private class CustomFactory : SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory {
        override fun create(hub: IHub, options: SentryOptions): SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget? {
            return null
        }
    }
}
