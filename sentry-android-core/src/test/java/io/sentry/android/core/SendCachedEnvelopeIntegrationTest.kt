package io.sentry.android.core

import io.sentry.IConnectionStatusProvider
import io.sentry.IConnectionStatusProvider.ConnectionStatus
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory
import io.sentry.SentryExecutorService
import io.sentry.SentryLevel.DEBUG
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.transport.RateLimiter
import io.sentry.util.LazyEvaluator
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import org.awaitility.kotlin.await
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SendCachedEnvelopeIntegrationTest {
  private class Fixture {
    val scopes: IScopes = mock()
    val options = SentryAndroidOptions()
    val logger = mock<ILogger>()
    val factory = mock<SendFireAndForgetFactory>()
    val flag = AtomicBoolean(true)
    val sender = mock<SendFireAndForget>()

    fun getSut(
      cacheDirPath: String? = "abc",
      hasStartupCrashMarker: Boolean = false,
      hasSender: Boolean = true,
      delaySend: Long = 0L,
      taskFails: Boolean = false,
      mockExecutorService: ISentryExecutorService? = null,
    ): SendCachedEnvelopeIntegration {
      options.cacheDirPath = cacheDirPath
      options.setLogger(logger)
      options.isDebug = true
      options.executorService = mockExecutorService ?: SentryExecutorService()

      whenever(sender.send()).then {
        Thread.sleep(delaySend)
        if (taskFails) {
          throw ExecutionException(RuntimeException("Something went wrong"))
        }
        flag.set(false)
      }
      whenever(factory.hasValidPath(any(), any())).thenCallRealMethod()
      whenever(factory.create(any(), any()))
        .thenReturn(
          if (hasSender) {
            sender
          } else {
            null
          }
        )

      return SendCachedEnvelopeIntegration(factory, LazyEvaluator { hasStartupCrashMarker })
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when cacheDirPath is not set, does nothing`() {
    val sut = fixture.getSut(cacheDirPath = null)

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.factory, never()).create(any(), any())
  }

  @Test
  fun `when factory returns null, does nothing`() {
    val sut = fixture.getSut(hasSender = false, mockExecutorService = ImmediateExecutorService())

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.factory).create(any(), any())
    verify(fixture.sender, never()).send()
  }

  @Test
  fun `when has factory and cacheDirPath set, submits task into queue`() {
    val sut = fixture.getSut(mockExecutorService = ImmediateExecutorService())

    sut.register(fixture.scopes, fixture.options)

    await.untilFalse(fixture.flag)
    verify(fixture.sender).send()
  }

  @Test
  fun `when executorService is fake, does nothing`() {
    val sut = fixture.getSut(mockExecutorService = mock())
    sut.register(fixture.scopes, fixture.options)

    verify(fixture.factory, never()).create(any(), any())
    verify(fixture.sender, never()).send()
  }

  @Test
  fun `when has startup crash marker, awaits the task on the calling thread`() {
    val sut = fixture.getSut(hasStartupCrashMarker = true)

    sut.register(fixture.scopes, fixture.options)

    // we do not need to await here, because it's executed synchronously
    verify(fixture.sender).send()
  }

  @Test
  fun `when synchronous send times out, continues the task on a background thread`() {
    val sut = fixture.getSut(hasStartupCrashMarker = true, delaySend = 1000)
    fixture.options.startupCrashFlushTimeoutMillis = 100

    sut.register(fixture.scopes, fixture.options)

    // first wait until synchronous send times out and check that the logger was hit in the catch
    // block
    await.atLeast(500, MILLISECONDS)
    verify(fixture.logger)
      .log(eq(DEBUG), eq("Synchronous send timed out, continuing in the background."))

    // then wait until the async send finishes in background
    await.untilFalse(fixture.flag)
    verify(fixture.sender).send()
  }

  @Test
  fun `registers for network connection changes`() {
    val sut =
      fixture.getSut(
        hasStartupCrashMarker = false,
        mockExecutorService = ImmediateExecutorService(),
      )

    val connectionStatusProvider = mock<IConnectionStatusProvider>()
    fixture.options.connectionStatusProvider = connectionStatusProvider

    sut.register(fixture.scopes, fixture.options)
    verify(connectionStatusProvider).addConnectionStatusObserver(any())
  }

  @Test
  fun `when theres no network connection does nothing`() {
    val sut = fixture.getSut(hasStartupCrashMarker = false)

    val connectionStatusProvider = mock<IConnectionStatusProvider>()
    fixture.options.connectionStatusProvider = connectionStatusProvider

    whenever(connectionStatusProvider.connectionStatus).thenReturn(ConnectionStatus.DISCONNECTED)

    sut.register(fixture.scopes, fixture.options)
    verify(fixture.sender, never()).send()
  }

  @Test
  fun `when the network is not disconnected the factory is initialized`() {
    val sut =
      fixture.getSut(
        hasStartupCrashMarker = false,
        mockExecutorService = ImmediateExecutorService(),
      )

    val connectionStatusProvider = mock<IConnectionStatusProvider>()
    fixture.options.connectionStatusProvider = connectionStatusProvider

    whenever(connectionStatusProvider.connectionStatus).thenReturn(ConnectionStatus.UNKNOWN)

    sut.register(fixture.scopes, fixture.options)
    verify(fixture.factory).create(any(), any())
  }

  @Test
  fun `whenever network connection status changes, retries sending for relevant statuses`() {
    val sut =
      fixture.getSut(
        hasStartupCrashMarker = false,
        mockExecutorService = ImmediateExecutorService(),
      )

    val connectionStatusProvider = mock<IConnectionStatusProvider>()
    fixture.options.connectionStatusProvider = connectionStatusProvider
    whenever(connectionStatusProvider.connectionStatus).thenReturn(ConnectionStatus.DISCONNECTED)
    sut.register(fixture.scopes, fixture.options)

    // when there's no connection no factory create call should be done
    verify(fixture.sender, never()).send()

    // but for any other status processing should be triggered
    // CONNECTED
    whenever(connectionStatusProvider.connectionStatus).thenReturn(ConnectionStatus.CONNECTED)
    sut.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
    verify(fixture.sender).send()

    // UNKNOWN
    whenever(connectionStatusProvider.connectionStatus).thenReturn(ConnectionStatus.UNKNOWN)
    sut.onConnectionStatusChanged(ConnectionStatus.UNKNOWN)
    verify(fixture.sender, times(2)).send()

    // NO_PERMISSION
    whenever(connectionStatusProvider.connectionStatus).thenReturn(ConnectionStatus.NO_PERMISSION)
    sut.onConnectionStatusChanged(ConnectionStatus.NO_PERMISSION)
    verify(fixture.sender, times(3)).send()
  }

  @Test
  fun `when rate limiter is active, does not send envelopes`() {
    val sut = fixture.getSut(hasStartupCrashMarker = false)
    val rateLimiter =
      mock<RateLimiter> { whenever(mock.isActiveForCategory(any())).thenReturn(true) }
    whenever(fixture.scopes.rateLimiter).thenReturn(rateLimiter)

    sut.register(fixture.scopes, fixture.options)

    // no factory call should be done if there's rate limiting active
    verify(fixture.sender, never()).send()
  }

  @Test
  fun `when closed after register, does nothing`() {
    val deferredExecutorService = DeferredExecutorService()
    val sut = fixture.getSut(mockExecutorService = deferredExecutorService)

    sut.register(fixture.scopes, fixture.options)
    verify(fixture.sender, never()).send()
    sut.close()

    deferredExecutorService.runAll()
    verify(fixture.sender, never()).send()
  }
}
