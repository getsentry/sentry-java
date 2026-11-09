package io.sentry.android.core.internal.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.IConnectionStatusProvider.ConnectionStatus
import io.sentry.IConnectionStatusProvider.IConnectionStatusObserver
import io.sentry.ILogger
import io.sentry.ISentryExecutorService
import io.sentry.SentryOptions
import io.sentry.android.core.AppState
import io.sentry.android.core.BuildInfoProvider
import io.sentry.android.core.ContextUtils
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.time.TestMonotonicTicker
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/**
 * Scenarios in which the SDK executor, network callbacks and callers such as the main thread use
 * the provider at the same time. Slow system service calls are stalled with a [Stall], and calls
 * that must not wait for them run on a separate caller thread with a bounded wait.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.S])
class AndroidConnectionStatusProviderConcurrencyTest {

  @AfterTest
  fun `tear down`() {
    Fixture.closeAll()
  }

  // Caller-side calls while the network callback registration is stalled

  @Test
  fun `addConnectionStatusObserver does not wait for a stalled network callback registration`() {
    val fixture = Fixture.create()
    val stall = fixture.stallNetworkCallbackRegistration()
    val connectionStatusProvider = fixture.createProvider()
    stall.awaitEntered()
    val observer = mock<IConnectionStatusObserver>()

    val added = fixture.onCallerThread {
      connectionStatusProvider.addConnectionStatusObserver(observer)
    }

    assertThat(added).isTrue()
    assertThat(connectionStatusProvider.statusObservers).containsExactly(observer)
  }

  @Test
  fun `getConnectionStatus refreshes a stale cache without waiting for a stalled network callback registration`() {
    val fixture = Fixture.create()
    val stall = fixture.stallNetworkCallbackRegistration()
    val connectionStatusProvider = fixture.createProvider()
    stall.awaitEntered()

    val status = fixture.onCallerThread { connectionStatusProvider.connectionStatus }

    assertThat(status).isEqualTo(ConnectionStatus.CONNECTED)
  }

  @Test
  fun `getConnection refreshes a stale cache without waiting for a stalled network callback registration`() {
    val fixture = Fixture.create()
    val stall = fixture.stallNetworkCallbackRegistration()
    val connectionStatusProvider = fixture.createProvider()
    stall.awaitEntered()

    val connection = fixture.onCallerThread { connectionStatusProvider.connection }

    assertThat(connection.type).isEqualTo("wifi")
  }

  // Caller-side calls while the telephony callback registration is stalled

  @Test
  fun `addConnectionStatusObserver does not wait for a stalled telephony callback registration`() {
    val fixture = Fixture.create(sdkVersion = Build.VERSION_CODES.S)
    val stall = fixture.stallTelephonyCallbackRegistration()
    val connectionStatusProvider = fixture.createProvider()
    stall.awaitEntered()
    val observer = mock<IConnectionStatusObserver>()

    val added = fixture.onCallerThread {
      connectionStatusProvider.addConnectionStatusObserver(observer)
    }

    assertThat(added).isTrue()
    assertThat(connectionStatusProvider.statusObservers).containsExactly(observer)
  }

  @Test
  fun `removeConnectionStatusObserver does not wait for a stalled telephony callback registration`() {
    val fixture = Fixture.create(sdkVersion = Build.VERSION_CODES.S)
    val stall = fixture.stallTelephonyCallbackRegistration()
    val connectionStatusProvider = fixture.createProvider()
    stall.awaitEntered()
    val observer = mock<IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    fixture.onCallerThread { connectionStatusProvider.removeConnectionStatusObserver(observer) }

    assertThat(connectionStatusProvider.statusObservers).isEmpty()
  }

  @Test
  fun `getConnectionStatus refreshes a stale cache without waiting for a stalled telephony callback registration`() {
    val fixture = Fixture.create(sdkVersion = Build.VERSION_CODES.S)
    val stall = fixture.stallTelephonyCallbackRegistration()
    val connectionStatusProvider = fixture.createProvider()
    stall.awaitEntered()

    val status = fixture.onCallerThread { connectionStatusProvider.connectionStatus }

    assertThat(status).isEqualTo(ConnectionStatus.CONNECTED)
  }

  // Caller-side calls while unregistering in the background is stalled

  @Test
  fun `addConnectionStatusObserver does not wait for a stalled network callback unregistration`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val stall = fixture.stallNetworkCallbackUnregistration()
    connectionStatusProvider.onBackground()
    stall.awaitEntered()
    val observer = mock<IConnectionStatusObserver>()

    val added = fixture.onCallerThread {
      connectionStatusProvider.addConnectionStatusObserver(observer)
    }

    assertThat(added).isTrue()
    assertThat(connectionStatusProvider.statusObservers).containsExactly(observer)
  }

  @Test
  fun `getConnectionStatus refreshes a stale cache without waiting for a stalled network callback unregistration`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val stall = fixture.stallNetworkCallbackUnregistration()
    connectionStatusProvider.onBackground()
    stall.awaitEntered()

    val status = fixture.onCallerThread { connectionStatusProvider.connectionStatus }

    assertThat(status).isEqualTo(ConnectionStatus.CONNECTED)
  }

  @Test
  fun `addConnectionStatusObserver does not wait for a stalled telephony callback unregistration`() {
    val fixture = Fixture.create(sdkVersion = Build.VERSION_CODES.S)
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val stall = fixture.stallTelephonyCallbackUnregistration()
    connectionStatusProvider.onBackground()
    stall.awaitEntered()
    val observer = mock<IConnectionStatusObserver>()

    val added = fixture.onCallerThread {
      connectionStatusProvider.addConnectionStatusObserver(observer)
    }

    assertThat(added).isTrue()
    assertThat(connectionStatusProvider.statusObservers).containsExactly(observer)
  }

  @Test
  fun `getConnectionStatus refreshes a stale cache without waiting for a stalled telephony callback unregistration`() {
    val fixture = Fixture.create(sdkVersion = Build.VERSION_CODES.S)
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val stall = fixture.stallTelephonyCallbackUnregistration()
    connectionStatusProvider.onBackground()
    stall.awaitEntered()

    val status = fixture.onCallerThread { connectionStatusProvider.connectionStatus }

    assertThat(status).isEqualTo(ConnectionStatus.CONNECTED)
  }

  // A stale-cache refresh racing a network callback

  @Test
  fun `a stale-cache refresh stores the queried capabilities of the active network`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()

    connectionStatusProvider.connectionStatus

    assertThat(connectionStatusProvider.cachedNetworkCapabilities)
      .isSameInstanceAs(fixture.wifiCapabilities)
  }

  @Test
  fun `capabilities changed while a stale-cache refresh queries the network capabilities are kept`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    networkCallback.onAvailable(fixture.network)
    val stall = fixture.stallNetworkCapabilitiesQuery()
    val refresh = fixture.startOnCallerThread { connectionStatusProvider.connectionStatus }
    stall.awaitEntered()

    fixture.onCallerThread {
      networkCallback.onCapabilitiesChanged(fixture.network, fixture.cellularCapabilities)
    }
    stall.release()
    refresh.get(TIMEOUT_SECONDS, SECONDS)

    assertThat(connectionStatusProvider.connectionType).isEqualTo("cellular")
  }

  @Test
  fun `a network lost while a stale-cache refresh queries the network capabilities keeps the cache cleared`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    networkCallback.onAvailable(fixture.network)
    val stall = fixture.stallNetworkCapabilitiesQuery()
    val refresh = fixture.startOnCallerThread { connectionStatusProvider.connectionStatus }
    stall.awaitEntered()

    fixture.onCallerThread { networkCallback.onLost(fixture.network) }
    stall.release()
    refresh.get(TIMEOUT_SECONDS, SECONDS)

    assertThat(connectionStatusProvider.cachedNetworkCapabilities).isNull()
  }

  @Test
  fun `a network that becomes available while a stale-cache refresh queries the network capabilities stays the current network`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    val observer = mock<IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)
    val stall = fixture.stallNetworkCapabilitiesQuery()
    val refresh = fixture.startOnCallerThread { connectionStatusProvider.connectionStatus }
    stall.awaitEntered()

    fixture.onCallerThread { networkCallback.onAvailable(fixture.network) }
    stall.release()
    refresh.get(TIMEOUT_SECONDS, SECONDS)
    networkCallback.onLost(fixture.network)

    verify(observer).onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
  }

  // Observers changing the observers during a notification

  @Test
  fun `an observer that removes itself during a notification does not keep the other observers from being notified`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    networkCallback.onAvailable(fixture.network)
    val selfRemovingObserver =
      object : IConnectionStatusObserver {
        override fun onConnectionStatusChanged(status: ConnectionStatus) {
          connectionStatusProvider.removeConnectionStatusObserver(this)
        }
      }
    val otherObserver = mock<IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(selfRemovingObserver)
    connectionStatusProvider.addConnectionStatusObserver(otherObserver)

    networkCallback.onLost(fixture.network)

    verify(otherObserver).onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
    assertThat(connectionStatusProvider.statusObservers).containsExactly(otherObserver)
  }

  @Test
  fun `an observer added during a notification only receives later notifications`() {
    val fixture = Fixture.create()
    val connectionStatusProvider = fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    networkCallback.onAvailable(fixture.network)
    val addedObserver = mock<IConnectionStatusObserver>()
    val addingObserver =
      object : IConnectionStatusObserver {
        override fun onConnectionStatusChanged(status: ConnectionStatus) {
          connectionStatusProvider.addConnectionStatusObserver(addedObserver)
        }
      }
    connectionStatusProvider.addConnectionStatusObserver(addingObserver)

    networkCallback.onLost(fixture.network)

    verifyNoInteractions(addedObserver)
    assertThat(connectionStatusProvider.statusObservers).contains(addedObserver)

    networkCallback.onUnavailable()

    verify(addedObserver).onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
  }

  // Child network callbacks

  @Test
  fun `addNetworkCallback does not wait for a child network callback that is being notified`() {
    val fixture = Fixture.create()
    fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    val stall = fixture.newStall()
    fixture.addChildNetworkCallback(
      object : NetworkCallback() {
        override fun onAvailable(network: Network) {
          stall.block(Unit)
        }
      }
    )
    fixture.startOnCallerThread { networkCallback.onAvailable(fixture.network) }
    stall.awaitEntered()
    val childNetworkCallback = mock<NetworkCallback>()

    val added = fixture.onCallerThread {
      AndroidConnectionStatusProvider.addNetworkCallback(
        fixture.context,
        fixture.logger,
        fixture.buildInfo,
        childNetworkCallback,
      )
    }

    assertThat(added).isTrue()
    assertThat(AndroidConnectionStatusProvider.getChildCallbacks()).contains(childNetworkCallback)
  }

  @Test
  fun `removeNetworkCallback does not wait for a child network callback that is being notified`() {
    val fixture = Fixture.create()
    fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    val stall = fixture.newStall()
    fixture.addChildNetworkCallback(
      object : NetworkCallback() {
        override fun onAvailable(network: Network) {
          stall.block(Unit)
        }
      }
    )
    val childNetworkCallback = mock<NetworkCallback>()
    fixture.addChildNetworkCallback(childNetworkCallback)
    fixture.startOnCallerThread { networkCallback.onAvailable(fixture.network) }
    stall.awaitEntered()

    fixture.onCallerThread {
      AndroidConnectionStatusProvider.removeNetworkCallback(childNetworkCallback)
    }

    assertThat(AndroidConnectionStatusProvider.getChildCallbacks())
      .doesNotContain(childNetworkCallback)
  }

  @Test
  fun `a child network callback that removes itself during a notification does not keep the other child network callbacks from being notified`() {
    val fixture = Fixture.create()
    fixture.createRegisteredProvider()
    val networkCallback = fixture.captureNetworkCallback()
    fixture.addChildNetworkCallback(
      object : NetworkCallback() {
        override fun onAvailable(network: Network) {
          AndroidConnectionStatusProvider.removeNetworkCallback(this)
        }
      }
    )
    val otherChildNetworkCallback = mock<NetworkCallback>()
    fixture.addChildNetworkCallback(otherChildNetworkCallback)

    networkCallback.onAvailable(fixture.network)

    verify(otherChildNetworkCallback).onAvailable(fixture.network)
    assertThat(AndroidConnectionStatusProvider.getChildCallbacks())
      .containsExactly(otherChildNetworkCallback)
  }

  // Foreground and background transitions queued behind other executor tasks

  @Test
  fun `backgrounding while the initial network callback registration is queued unregisters the network callback`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()

    connectionStatusProvider.onBackground()
    executor.runAll()

    assertThat(connectionStatusProvider.networkCallback).isNull()
    verify(fixture.connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
  }

  @Test
  fun `backgrounding while the initial network callback registration is queued unregisters the telephony callback`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(sdkVersion = Build.VERSION_CODES.S, executorService = executor)
    val connectionStatusProvider = fixture.createProvider()

    connectionStatusProvider.onBackground()
    executor.runAll()

    verify(fixture.telephonyManager).unregisterTelephonyCallback(any())
  }

  @Test
  fun `foregrounding while a background unregistration is queued registers the network callback again`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()
    executor.runAll()

    connectionStatusProvider.onBackground()
    connectionStatusProvider.onForeground()
    executor.runAll()

    assertThat(connectionStatusProvider.networkCallback).isNotNull()
    verify(fixture.connectivityManager, times(2)).registerDefaultNetworkCallback(any())
  }

  @Test
  fun `adding an observer while a background unregistration is queued registers the network callback again`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()
    executor.runAll()

    connectionStatusProvider.onBackground()
    connectionStatusProvider.addConnectionStatusObserver(mock())
    executor.runAll()

    assertThat(connectionStatusProvider.networkCallback).isNotNull()
    verify(fixture.connectivityManager, times(2)).registerDefaultNetworkCallback(any())
  }

  // Registrations queued around close

  @Test
  fun `closing after the network callback registration ran unregisters the network callback`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()
    executor.runAll()

    connectionStatusProvider.close()
    executor.runAll()

    assertThat(connectionStatusProvider.networkCallback).isNull()
    verify(fixture.connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
  }

  @Test
  fun `closing while the network callback registration is running unregisters the network callback`() {
    val fixture = Fixture.create()
    val stall = fixture.stallNetworkCallbackRegistration()
    val connectionStatusProvider = fixture.createProvider()
    stall.awaitEntered()

    connectionStatusProvider.close()
    stall.release()
    fixture.awaitExecutorIdle()

    assertThat(connectionStatusProvider.networkCallback).isNull()
    verify(fixture.connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
  }

  @Test
  fun `closing while the initial network callback registration is queued does not register the network callback`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()

    connectionStatusProvider.close()
    executor.runAll()

    assertThat(connectionStatusProvider.networkCallback).isNull()
    verify(fixture.connectivityManager, never()).registerDefaultNetworkCallback(any())
  }

  @Test
  fun `adding an observer after close does not register the network callback again`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()
    executor.runAll()

    connectionStatusProvider.close()
    connectionStatusProvider.addConnectionStatusObserver(mock())
    executor.runAll()

    assertThat(connectionStatusProvider.networkCallback).isNull()
    verify(fixture.connectivityManager, times(1)).registerDefaultNetworkCallback(any())
  }

  @Test
  fun `adding an observer after close does not register the telephony callback again`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(sdkVersion = Build.VERSION_CODES.S, executorService = executor)
    val connectionStatusProvider = fixture.createProvider()
    executor.runAll()

    connectionStatusProvider.close()
    connectionStatusProvider.addConnectionStatusObserver(mock())
    executor.runAll()

    verify(fixture.telephonyManager, times(1)).registerTelephonyCallback(any(), any())
  }

  @Test
  fun `foregrounding after close does not register the network callback again`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()
    executor.runAll()

    connectionStatusProvider.close()
    connectionStatusProvider.onForeground()
    executor.runAll()

    assertThat(connectionStatusProvider.networkCallback).isNull()
    verify(fixture.connectivityManager, times(1)).registerDefaultNetworkCallback(any())
  }

  @Test
  fun `foregrounding after close does not notify child network callbacks of a lost network`() {
    val executor = DeferredExecutorService()
    val fixture = Fixture.create(executorService = executor)
    val connectionStatusProvider = fixture.createProvider()
    executor.runAll()
    connectionStatusProvider.close()
    executor.runAll()
    val childNetworkCallback = mock<NetworkCallback>()
    fixture.addChildNetworkCallback(childNetworkCallback)
    whenever(fixture.connectivityManager.activeNetwork).thenReturn(null)

    connectionStatusProvider.onForeground()
    executor.runAll()

    verify(childNetworkCallback, never()).onLost(anyOrNull())
  }

  /** Blocks a mocked system service call until released, or until a timeout elapses. */
  private class Stall {
    private val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)

    fun <T> block(result: T): T {
      entered.countDown()
      released.await(STALL_TIMEOUT_SECONDS, SECONDS)
      return result
    }

    fun awaitEntered() {
      assertThat(entered.await(TIMEOUT_SECONDS, SECONDS)).isTrue()
    }

    fun release() {
      released.countDown()
    }
  }

  /**
   * Runs tasks in order on a single thread, like the SDK executor. Mockito's static mocks only
   * apply to the thread that creates them, so the thread mocks
   * [ContextUtils.isForegroundImportance] itself.
   */
  private class SingleThreadExecutorService : ISentryExecutorService {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
      Thread(it, "test-sentry-executor").apply { isDaemon = true }
    }
    private lateinit var contextUtils: MockedStatic<ContextUtils>

    init {
      executor.submit { contextUtils = mockForegroundImportance() }.get(TIMEOUT_SECONDS, SECONDS)
    }

    fun awaitIdle() {
      executor.submit {}.get(TIMEOUT_SECONDS, SECONDS)
    }

    fun shutdown() {
      executor.submit { contextUtils.close() }.get(TIMEOUT_SECONDS, SECONDS)
      executor.shutdown()
      executor.awaitTermination(TIMEOUT_SECONDS, SECONDS)
    }

    override fun submit(runnable: Runnable): Future<*> = executor.submit(runnable)

    override fun <T> submit(callable: Callable<T>): Future<T> = executor.submit(callable)

    override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> =
      throw UnsupportedOperationException()

    override fun close(timeoutMillis: Long) {}

    override fun isClosed(): Boolean = executor.isShutdown
  }

  private class Fixture
  private constructor(
    sdkVersion: Int,
    private val executorService: ISentryExecutorService,
  ) {
    val context = mock<Context>()
    val connectivityManager = mock<ConnectivityManager>()
    val telephonyManager = mock<TelephonyManager>()
    val buildInfo = mock<BuildInfoProvider>()
    val logger = mock<ILogger>()
    val network = mock<Network>()
    val wifiCapabilities = capabilities(TRANSPORT_WIFI)
    val cellularCapabilities = capabilities(TRANSPORT_CELLULAR)
    private val stalls = mutableListOf<Stall>()
    private val callerThreads = mutableListOf<Thread>()

    private val options = SentryOptions()
    private val contextUtils = mockForegroundImportance()
    private var connectionStatusProvider: AndroidConnectionStatusProvider? = null

    init {
      whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE))
        .thenReturn(connectivityManager)
      whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)
      whenever(context.checkPermission(eq(Manifest.permission.ACCESS_NETWORK_STATE), any(), any()))
        .thenReturn(PERMISSION_GRANTED)
      whenever(buildInfo.sdkInfoVersion).thenReturn(sdkVersion)
      whenever(connectivityManager.activeNetwork).thenReturn(network)
      whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(wifiCapabilities)

      // Another suite can leave its connectivity manager and child callbacks in the static fields.
      AndroidConnectionStatusProvider::class
        .java
        .getDeclaredField("connectivityManager")
        .apply { isAccessible = true }
        .set(null, null)
      AndroidConnectionStatusProvider.getChildCallbacks().clear()

      options.setLogger(logger)
      options.executorService = executorService

      AppState.getInstance().resetInstance()
      AppState.getInstance().registerLifecycleObserver(options)
    }

    fun createProvider(): AndroidConnectionStatusProvider =
      AndroidConnectionStatusProvider(context, options, buildInfo, TestMonotonicTicker()).also {
        connectionStatusProvider = it
      }

    /** Creates a provider and waits until its network callback registration has run. */
    fun createRegisteredProvider(): AndroidConnectionStatusProvider =
      createProvider().also { awaitExecutorIdle() }

    fun awaitExecutorIdle() {
      (executorService as SingleThreadExecutorService).awaitIdle()
    }

    fun captureNetworkCallback(): NetworkCallback {
      val captor = argumentCaptor<NetworkCallback>()
      verify(connectivityManager).registerDefaultNetworkCallback(captor.capture())
      return captor.lastValue
    }

    fun addChildNetworkCallback(networkCallback: NetworkCallback) {
      AndroidConnectionStatusProvider.addNetworkCallback(
        context,
        logger,
        buildInfo,
        networkCallback,
      )
    }

    fun stallNetworkCallbackRegistration(): Stall =
      newStall().also { stall ->
        doAnswer { stall.block(null) }
          .whenever(connectivityManager)
          .registerDefaultNetworkCallback(any())
      }

    fun stallNetworkCallbackUnregistration(): Stall =
      newStall().also { stall ->
        doAnswer { stall.block(null) }
          .whenever(connectivityManager)
          .unregisterNetworkCallback(any<NetworkCallback>())
      }

    fun stallTelephonyCallbackRegistration(): Stall =
      newStall().also { stall ->
        doAnswer { stall.block(null) }
          .whenever(telephonyManager)
          .registerTelephonyCallback(any(), any())
      }

    fun stallTelephonyCallbackUnregistration(): Stall =
      newStall().also { stall ->
        doAnswer { stall.block(null) }
          .whenever(telephonyManager)
          .unregisterTelephonyCallback(any<TelephonyCallback>())
      }

    fun stallNetworkCapabilitiesQuery(): Stall =
      newStall().also { stall ->
        whenever(connectivityManager.getNetworkCapabilities(any())).thenAnswer {
          stall.block(wifiCapabilities)
        }
      }

    fun newStall(): Stall = Stall().also { stalls += it }

    /** Starts [action] on a new thread, standing in for a caller such as the main thread. */
    fun <T> startOnCallerThread(action: () -> T): FutureTask<T> =
      FutureTask(action).also { task ->
        callerThreads += Thread(task, "test-caller").apply { isDaemon = true }.also { it.start() }
      }

    /** Runs [action] on a new caller thread and fails if it does not complete in time. */
    fun <T> onCallerThread(action: () -> T): T {
      val task = startOnCallerThread(action)
      try {
        return task.get(TIMEOUT_SECONDS, SECONDS)
      } catch (e: TimeoutException) {
        throw AssertionError("The call did not complete within $TIMEOUT_SECONDS seconds", e)
      }
    }

    fun close() {
      stalls.forEach { it.release() }
      // A caller that is still running after close() would cache this test's connectivity
      // manager in the static field that close() resets, and leak it into the next test.
      callerThreads.forEach { it.join(SECONDS.toMillis(TIMEOUT_SECONDS)) }
      connectionStatusProvider?.close()
      when (executorService) {
        is SingleThreadExecutorService -> executorService.shutdown()
        is DeferredExecutorService -> executorService.runAll()
      }
      contextUtils.close()
      AppState.getInstance().unregisterLifecycleObserver()
    }

    companion object {
      private val created = mutableListOf<Fixture>()

      fun create(
        sdkVersion: Int = Build.VERSION_CODES.N,
        executorService: ISentryExecutorService = SingleThreadExecutorService(),
      ): Fixture {
        resetStaticState()
        return Fixture(sdkVersion, executorService).also { created += it }
      }

      fun closeAll() {
        created.forEach { it.close() }
        created.clear()
      }

      /**
       * Clears the cached ConnectivityManager and the child network callbacks, which are static and
       * may have been left behind by other suites, by closing a provider that registers nothing.
       */
      private fun resetStaticState() {
        val resetOptions = SentryOptions().apply { executorService = ImmediateExecutorService() }
        val foregroundImportance =
          mockStatic(ContextUtils::class.java, CALLS_REAL_METHODS).also {
            it.`when`<Boolean> { ContextUtils.isForegroundImportance() }.thenReturn(false)
          }
        try {
          AndroidConnectionStatusProvider(mock(), resetOptions, mock(), TestMonotonicTicker())
            .close()
        } finally {
          foregroundImportance.close()
        }
      }

      private fun capabilities(transport: Int): NetworkCapabilities {
        val capabilities = mock<NetworkCapabilities>()
        whenever(capabilities.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
        whenever(capabilities.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
        whenever(capabilities.hasTransport(transport)).thenReturn(true)
        return capabilities
      }
    }
  }

  private companion object {
    /** Bounds every wait, so a regression fails the test instead of hanging the build. */
    const val TIMEOUT_SECONDS = 5L

    /**
     * Bounds a stalled call, which the test releases itself. Much longer than [TIMEOUT_SECONDS], so
     * that a caller waiting for the stalled call times out before the stall gives up.
     */
    const val STALL_TIMEOUT_SECONDS = 30L

    fun mockForegroundImportance(): MockedStatic<ContextUtils> =
      mockStatic(ContextUtils::class.java, CALLS_REAL_METHODS).also {
        it.`when`<Boolean> { ContextUtils.isForegroundImportance() }.thenReturn(true)
      }
  }
}
