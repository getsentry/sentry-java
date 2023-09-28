package io.sentry

import io.sentry.SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget
import io.sentry.protocol.SdkVersion
import io.sentry.test.ImmediateExecutorService
import io.sentry.transport.RateLimiter
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
        val sender = mock<SendFireAndForget>()
        var callback = mock<CustomFactory>().apply {
            whenever(hasValidPath(any(), any())).thenCallRealMethod()
            whenever(create(any(), any())).thenReturn(sender)
        }

        init {
            options.setDebug(true)
            options.setLogger(logger)
            options.sdkVersion = SdkVersion("test", "1.2.3")
        }

        fun getSut(useImmediateExecutor: Boolean = true): SendCachedEnvelopeFireAndForgetIntegration {
            if (useImmediateExecutor) {
                options.executorService = ImmediateExecutorService()
            }
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
        verify(fixture.sender, never()).send()
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
        sut.register(fixture.hub, fixture.options)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), eq("SendFireAndForget factory is null."))
        verify(fixture.sender, never()).send()
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
        val sut = fixture.getSut(useImmediateExecutor = false)
        sut.register(fixture.hub, fixture.options)
        verify(fixture.logger).log(eq(SentryLevel.ERROR), eq("Failed to call the executor. Cached events will not be sent. Did you call Sentry.close()?"), any())
    }

    @Test
    fun `registers for network connection changes`() {
        val connectionStatusProvider = mock<IConnectionStatusProvider>()
        fixture.options.connectionStatusProvider = connectionStatusProvider
        fixture.options.cacheDirPath = "cache"

        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        verify(connectionStatusProvider).addConnectionStatusObserver(any())
    }

    @Test
    fun `when theres no network connection does nothing`() {
        val connectionStatusProvider = mock<IConnectionStatusProvider>()
        whenever(connectionStatusProvider.connectionStatus).thenReturn(
            IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
        )
        fixture.options.connectionStatusProvider = connectionStatusProvider
        fixture.options.cacheDirPath = "cache"

        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.register(fixture.hub, fixture.options)
        verify(fixture.sender, never()).send()
    }

    @Test
    fun `when the network is not disconnected the factory is initialized`() {
        val connectionStatusProvider = mock<IConnectionStatusProvider>()
        whenever(connectionStatusProvider.connectionStatus).thenReturn(
            IConnectionStatusProvider.ConnectionStatus.UNKNOWN
        )
        fixture.options.connectionStatusProvider = connectionStatusProvider
        fixture.options.cacheDirPath = "cache"

        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        verify(fixture.sender).send()
    }

    @Test
    fun `whenever network connection status changes, retries sending for relevant statuses`() {
        val connectionStatusProvider = mock<IConnectionStatusProvider>()
        whenever(connectionStatusProvider.connectionStatus).thenReturn(
            IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
        )
        fixture.options.connectionStatusProvider = connectionStatusProvider
        fixture.options.cacheDirPath = "cache"

        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        // when there's no connection no factory create call should be done
        verify(fixture.sender, never()).send()

        // but for any other status processing should be triggered
        // CONNECTED
        whenever(connectionStatusProvider.connectionStatus).thenReturn(IConnectionStatusProvider.ConnectionStatus.CONNECTED)
        sut.onConnectionStatusChanged(IConnectionStatusProvider.ConnectionStatus.CONNECTED)
        verify(fixture.sender).send()

        // UNKNOWN
        whenever(connectionStatusProvider.connectionStatus).thenReturn(IConnectionStatusProvider.ConnectionStatus.UNKNOWN)
        sut.onConnectionStatusChanged(IConnectionStatusProvider.ConnectionStatus.UNKNOWN)
        verify(fixture.sender, times(2)).send()

        // NO_PERMISSION
        whenever(connectionStatusProvider.connectionStatus).thenReturn(IConnectionStatusProvider.ConnectionStatus.NO_PERMISSION)
        sut.onConnectionStatusChanged(IConnectionStatusProvider.ConnectionStatus.NO_PERMISSION)
        verify(fixture.sender, times(3)).send()
    }

    @Test
    fun `when rate limiter is active, does not send envelopes`() {
        val sut = fixture.getSut()
        val rateLimiter = mock<RateLimiter> {
            whenever(mock.isActiveForCategory(any())).thenReturn(true)
        }
        whenever(fixture.hub.rateLimiter).thenReturn(rateLimiter)

        sut.register(fixture.hub, fixture.options)

        // no factory call should be done if there's rate limiting active
        verify(fixture.sender, never()).send()
    }

    private class CustomFactory : SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory {
        override fun create(hub: IHub, options: SentryOptions): SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget? {
            return null
        }
    }
}
