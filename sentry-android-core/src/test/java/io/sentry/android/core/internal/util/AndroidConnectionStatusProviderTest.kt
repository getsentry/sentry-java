package io.sentry.android.core.internal.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IConnectionStatusProvider
import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.android.core.AppState
import io.sentry.android.core.BuildInfoProvider
import io.sentry.android.core.ContextUtils
import io.sentry.android.core.SystemEventsBreadcrumbsIntegration
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.thread.IThreadChecker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.P])
class AndroidConnectionStatusProviderTest {
  private lateinit var connectionStatusProvider: AndroidConnectionStatusProvider
  private lateinit var contextMock: Context
  private lateinit var connectivityManager: ConnectivityManager
  private lateinit var networkInfo: NetworkInfo
  private lateinit var buildInfo: BuildInfoProvider
  private lateinit var timeProvider: ICurrentDateProvider
  private lateinit var options: SentryOptions
  private lateinit var network: Network
  private lateinit var networkCapabilities: NetworkCapabilities
  private lateinit var logger: ILogger
  private lateinit var contextUtilsStaticMock: MockedStatic<ContextUtils>

  private var currentTime = 1000L

  @BeforeTest
  fun beforeTest() {
    contextMock = mock()
    connectivityManager = mock()
    whenever(contextMock.getSystemService(Context.CONNECTIVITY_SERVICE))
      .thenReturn(connectivityManager)
    whenever(
        contextMock.checkPermission(eq(Manifest.permission.ACCESS_NETWORK_STATE), any(), any())
      )
      .thenReturn(PERMISSION_GRANTED)

    networkInfo = mock()
    whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)

    buildInfo = mock()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    network = mock()
    whenever(connectivityManager.activeNetwork).thenReturn(network)

    networkCapabilities = mock()
    whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(networkCapabilities)
    whenever(networkCapabilities.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(networkCapabilities.hasTransport(TRANSPORT_WIFI)).thenReturn(true)

    timeProvider = mock()
    whenever(timeProvider.currentTimeMillis).thenAnswer { currentTime }

    logger = mock()
    options = SentryOptions()
    options.setLogger(logger)
    options.executorService = ImmediateExecutorService()

    // Reset current time for each test to ensure cache isolation
    currentTime = 1000L

    // Mock ContextUtils to return foreground importance
    contextUtilsStaticMock = mockStatic(ContextUtils::class.java)
    contextUtilsStaticMock
      .`when`<Boolean> { ContextUtils.isForegroundImportance() }
      .thenReturn(true)
    contextUtilsStaticMock
      .`when`<Context> { ContextUtils.getApplicationContext(any()) }
      .thenReturn(contextMock)

    AppState.getInstance().resetInstance()
    AppState.getInstance().registerLifecycleObserver(options)

    connectionStatusProvider =
      AndroidConnectionStatusProvider(contextMock, options, buildInfo, timeProvider)
  }

  @AfterTest
  fun `tear down`() {
    // clear the cache and ensure proper cleanup
    connectionStatusProvider.close()
    contextUtilsStaticMock.close()
    AppState.getInstance().unregisterLifecycleObserver()
  }

  @Test
  fun `When network is active and connected with permission, return CONNECTED for isConnected`() {
    whenever(networkInfo.isConnected).thenReturn(true)

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.CONNECTED,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `When network is active but not connected with permission, return DISCONNECTED for isConnected`() {
    whenever(networkInfo.isConnected).thenReturn(false)

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.DISCONNECTED,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `When there's no permission, return NO_PERMISSION for isConnected`() {
    whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.NO_PERMISSION,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `When network is not active, return DISCONNECTED for isConnected`() {
    whenever(connectivityManager.activeNetwork).thenReturn(null)

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.DISCONNECTED,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `When ConnectivityManager is not available, return UNKNOWN for isConnected`() {
    // First close the existing provider to clean up static state
    connectionStatusProvider.close()

    // Create a fresh context mock that returns null for ConnectivityManager
    val nullConnectivityContext = mock<Context>()
    whenever(nullConnectivityContext.getSystemService(any())).thenReturn(null)
    whenever(
        nullConnectivityContext.checkPermission(
          eq(Manifest.permission.ACCESS_NETWORK_STATE),
          any(),
          any(),
        )
      )
      .thenReturn(PERMISSION_GRANTED)

    // Need to mock ContextUtils for the new provider as well
    contextUtilsStaticMock
      .`when`<Context> { ContextUtils.getApplicationContext(eq(nullConnectivityContext)) }
      .thenReturn(nullConnectivityContext)

    // Create a new provider with the null connectivity manager
    val providerWithNullConnectivity =
      AndroidConnectionStatusProvider(nullConnectivityContext, options, buildInfo, timeProvider)

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.UNKNOWN,
      providerWithNullConnectivity.connectionStatus,
    )

    providerWithNullConnectivity.close()
  }

  @Test
  fun `When cache is updating, return UNKNOWN for connectionStatus on main thread`() {
    whenever(networkInfo.isConnected).thenReturn(true)
    // When we are on the main thread
    val mockThreadChecker = mock<IThreadChecker>()
    options.threadChecker = mockThreadChecker
    whenever(mockThreadChecker.isMainThread()).thenReturn(true)

    // The update is done on the background
    val executorService = DeferredExecutorService()
    options.executorService = executorService

    // Advance time beyond TTL (2 minutes)
    currentTime += 2 * 60 * 1000L

    // Connection status is unknown while we update the cache
    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.UNKNOWN,
      connectionStatusProvider.connectionStatus,
    )

    verify(connectivityManager, never()).activeNetworkInfo
    verify(connectivityManager, never()).activeNetwork

    // When background cache update is done
    executorService.runAll()

    // Connection status is updated
    verify(connectivityManager).activeNetwork
    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.CONNECTED,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `When there's no permission, return null for getConnectionType`() {
    whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)

    assertNull(connectionStatusProvider.connectionType)
  }

  @Test
  fun `When network is not active, return null for getConnectionType`() {
    whenever(connectivityManager.activeNetwork).thenReturn(null)

    assertNull(connectionStatusProvider.connectionType)
  }

  @Test
  fun `When cache is updating, return null for getConnectionType on main thread`() {
    whenever(networkInfo.isConnected).thenReturn(true)
    // When we are on the main thread
    val mockThreadChecker = mock<IThreadChecker>()
    options.threadChecker = mockThreadChecker
    whenever(mockThreadChecker.isMainThread()).thenReturn(true)

    // The update is done on the background
    val executorService = DeferredExecutorService()
    options.executorService = executorService

    // Advance time beyond TTL (2 minutes)
    currentTime += 2 * 60 * 1000L

    // Connection type is null while we update the cache
    assertNull(connectionStatusProvider.connectionType)

    verify(connectivityManager, never()).activeNetworkInfo
    verify(connectivityManager, never()).activeNetwork

    // When background cache update is done
    executorService.runAll()

    // Connection type is updated
    verify(connectivityManager).activeNetwork
    assertNotNull(connectionStatusProvider.connectionType)
  }

  @Test
  fun `When network capabilities are not available, return null for getConnectionType`() {
    whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(null)

    assertNull(connectionStatusProvider.connectionType)
  }

  @Test
  fun `When network capabilities has TRANSPORT_WIFI, return wifi`() {
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_WIFI))).thenReturn(true)
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_CELLULAR))).thenReturn(false)
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_ETHERNET))).thenReturn(false)

    assertEquals("wifi", connectionStatusProvider.connectionType)
  }

  @Test
  fun `When network capabilities has TRANSPORT_ETHERNET, return ethernet`() {
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_ETHERNET))).thenReturn(true)
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_WIFI))).thenReturn(false)
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_CELLULAR))).thenReturn(false)

    assertEquals("ethernet", connectionStatusProvider.connectionType)
  }

  @Test
  fun `When network capabilities has TRANSPORT_CELLULAR, return cellular`() {
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_WIFI))).thenReturn(false)
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_ETHERNET))).thenReturn(false)
    whenever(networkCapabilities.hasTransport(eq(TRANSPORT_CELLULAR))).thenReturn(true)

    assertEquals("cellular", connectionStatusProvider.connectionType)
  }

  @Test
  fun `unregisterNetworkCallback calls connectivityManager unregisterDefaultNetworkCallback`() {
    whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
    AndroidConnectionStatusProvider.unregisterNetworkCallback(contextMock, logger, mock())

    verify(connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
  }

  @Test
  fun `When connectivityManager getActiveNetwork throws an exception, getConnectionType returns null`() {
    whenever(connectivityManager.activeNetwork).thenThrow(SecurityException("Android OS Bug"))

    assertNull(connectionStatusProvider.connectionType)
  }

  @Test
  fun `When connectivityManager registerDefaultCallback throws an exception, false is returned`() {
    whenever(connectivityManager.registerDefaultNetworkCallback(any()))
      .thenThrow(SecurityException("Android OS Bug"))
    assertFalse(
      AndroidConnectionStatusProvider.registerNetworkCallback(
        contextMock,
        logger,
        buildInfo,
        null,
        mock(),
      )
    )
  }

  @Test
  fun `connectionStatus returns NO_PERMISSIONS when context does not hold the permission`() {
    whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)
    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.NO_PERMISSION,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `adding and removing an observer works correctly`() {
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val observer = IConnectionStatusProvider.IConnectionStatusObserver {}
    val addResult = connectionStatusProvider.addConnectionStatusObserver(observer)
    assertTrue(addResult)
    assertEquals(1, connectionStatusProvider.statusObservers.size)
    assertNotNull(connectionStatusProvider.networkCallback)

    connectionStatusProvider.removeConnectionStatusObserver(observer)
    assertTrue(connectionStatusProvider.statusObservers.isEmpty())
  }

  @Test
  fun `cache TTL works correctly`() {
    // Setup: Mock network info to return connected
    whenever(networkInfo.isConnected).thenReturn(true)

    // For API level M, the code uses getActiveNetwork() and getNetworkCapabilities()
    // Let's track calls to these methods to verify caching behavior

    // Make the first call to establish baseline
    val firstResult = connectionStatusProvider.connectionStatus
    assertEquals(IConnectionStatusProvider.ConnectionStatus.CONNECTED, firstResult)

    // Count how many times getActiveNetwork was called so far (includes any initialization calls)
    val initialCallCount =
      mockingDetails(connectivityManager).invocations.count { it.method.name == "getActiveNetwork" }

    // Advance time by 1 minute (less than 2 minute TTL)
    currentTime += 60 * 1000L

    // Second call should use cache - no additional calls to getActiveNetwork
    val secondResult = connectionStatusProvider.connectionStatus
    assertEquals(IConnectionStatusProvider.ConnectionStatus.CONNECTED, secondResult)

    val callCountAfterSecond =
      mockingDetails(connectivityManager).invocations.count { it.method.name == "getActiveNetwork" }

    // Verify no additional calls were made (cache was used)
    assertEquals(initialCallCount, callCountAfterSecond, "Second call should use cache")

    // Advance time beyond TTL (total 3 minutes)
    currentTime += 2 * 60 * 1000L

    // Third call should refresh cache - should make new calls to getActiveNetwork
    val thirdResult = connectionStatusProvider.connectionStatus
    assertEquals(IConnectionStatusProvider.ConnectionStatus.CONNECTED, thirdResult)

    val callCountAfterThird =
      mockingDetails(connectivityManager).invocations.count { it.method.name == "getActiveNetwork" }

    // Verify that new calls were made (cache was refreshed)
    assertTrue(callCountAfterThird > callCountAfterSecond, "Third call should refresh cache")

    // All results should be consistent
    assertEquals(firstResult, secondResult)
    assertEquals(secondResult, thirdResult)
  }

  @Test
  fun `observers are only notified for significant changes`() {
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    // Get the callback that was registered
    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    // IMPORTANT: Set network as current first
    callback.onAvailable(network)

    // Create network capabilities for testing
    val oldCaps = mock<NetworkCapabilities>()
    whenever(oldCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(oldCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(oldCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)
    whenever(oldCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(false)
    whenever(oldCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    val newCaps = mock<NetworkCapabilities>()
    whenever(newCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(newCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(newCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)
    whenever(newCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(false)
    whenever(newCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    // First callback with capabilities - should notify
    callback.onCapabilitiesChanged(network, oldCaps)

    // Second callback with same significant capabilities - should NOT notify additional times
    callback.onCapabilitiesChanged(network, newCaps)

    // Only first change should trigger notification
    verify(observer, times(1)).onConnectionStatusChanged(any())
  }

  @Test
  fun `observers are notified when significant capabilities change`() {
    // Create a new provider with API level N for network callback support
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    // IMPORTANT: Set network as current first
    callback.onAvailable(network)

    val oldCaps = mock<NetworkCapabilities>()
    whenever(oldCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(oldCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(false) // Not validated
    whenever(oldCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)
    whenever(oldCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(false)
    whenever(oldCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    val newCaps = mock<NetworkCapabilities>()
    whenever(newCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(newCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true) // Now validated
    whenever(newCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)
    whenever(newCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(false)
    whenever(newCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    callback.onCapabilitiesChanged(network, oldCaps)
    callback.onCapabilitiesChanged(network, newCaps)

    // Should be notified for both changes (validation state changed)
    verify(observer, times(2)).onConnectionStatusChanged(any())
  }

  @Test
  fun `observers are notified when transport changes`() {
    // Create a new provider with API level N for network callback support
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    // IMPORTANT: Set network as current first
    callback.onAvailable(network)

    val wifiCaps = mock<NetworkCapabilities>()
    whenever(wifiCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(wifiCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(wifiCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)
    whenever(wifiCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(false)
    whenever(wifiCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    val cellularCaps = mock<NetworkCapabilities>()
    whenever(cellularCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(cellularCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(cellularCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(false)
    whenever(cellularCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(true)
    whenever(cellularCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    callback.onCapabilitiesChanged(network, wifiCaps)
    callback.onCapabilitiesChanged(network, cellularCaps)

    // Should be notified for both changes (transport changed)
    verify(observer, times(2)).onConnectionStatusChanged(any())
  }

  @Test
  fun `onLost clears cache and notifies with DISCONNECTED`() {
    // Create a new provider with API level N for network callback support
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    // IMPORTANT: Set network as current first
    callback.onAvailable(network)

    // Lose the network
    callback.onLost(network)

    assertNull(connectionStatusProvider.cachedNetworkCapabilities)
    verify(observer)
      .onConnectionStatusChanged(IConnectionStatusProvider.ConnectionStatus.DISCONNECTED)
  }

  @Test
  fun `onUnavailable clears cache and notifies with DISCONNECTED`() {
    // Create a new provider with API level N for network callback support
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    callback.onUnavailable()

    assertNull(connectionStatusProvider.cachedNetworkCapabilities)
    verify(observer)
      .onConnectionStatusChanged(IConnectionStatusProvider.ConnectionStatus.DISCONNECTED)
  }

  @Test
  fun `onLost for different network is ignored`() {
    // Create a new provider with API level N for network callback support
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    val network1 = mock<Network>()
    val network2 = mock<Network>()

    // Set current network
    callback.onAvailable(network1)

    // Lose a different network - should be ignored
    callback.onLost(network2)

    verifyNoInteractions(observer)
  }

  @Test
  fun `isNetworkEffectivelyConnected works correctly for Android 15`() {
    // Test case: has internet and validated capabilities with good transport
    val goodCaps = mock<NetworkCapabilities>()
    whenever(goodCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(goodCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(goodCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)
    whenever(goodCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(false)
    whenever(goodCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    // Override the mock to return good capabilities
    whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(goodCaps)

    // Force cache invalidation by advancing time beyond TTL
    currentTime += 3 * 60 * 1000L // 3 minutes

    // Should return CONNECTED for good capabilities
    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.CONNECTED,
      connectionStatusProvider.connectionStatus,
    )

    // Test case: missing validated capability
    val unvalidatedCaps = mock<NetworkCapabilities>()
    whenever(unvalidatedCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(unvalidatedCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(false)
    whenever(unvalidatedCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)

    whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(unvalidatedCaps)

    // Force cache invalidation again
    currentTime += 3 * 60 * 1000L

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.DISCONNECTED,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `API level below M falls back to legacy method`() {
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
    whenever(networkInfo.isConnected).thenReturn(true)

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.CONNECTED,
      connectionStatusProvider.connectionStatus,
    )
  }

  @Test
  fun `onCapabilitiesChanged updates cache`() {
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    // Set network as current first
    callback.onAvailable(network)

    // Create initial capabilities - CONNECTED state (wifi + validated)
    val initialCaps = mock<NetworkCapabilities>()
    whenever(initialCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(initialCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(initialCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(true)
    whenever(initialCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(false)
    whenever(initialCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    // First callback with initial capabilities
    callback.onCapabilitiesChanged(network, initialCaps)

    // Verify cache contains the initial capabilities
    assertEquals(initialCaps, connectionStatusProvider.cachedNetworkCapabilities)

    // Verify initial state - should be CONNECTED with wifi
    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.CONNECTED,
      connectionStatusProvider.connectionStatus,
    )
    assertEquals("wifi", connectionStatusProvider.connectionType)

    // Create new capabilities - DISCONNECTED state (cellular but not validated)
    val newCaps = mock<NetworkCapabilities>()
    whenever(newCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(newCaps.hasCapability(NET_CAPABILITY_VALIDATED))
      .thenReturn(false) // Not validated = DISCONNECTED
    whenever(newCaps.hasTransport(TRANSPORT_WIFI)).thenReturn(false)
    whenever(newCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(true)
    whenever(newCaps.hasTransport(TRANSPORT_ETHERNET)).thenReturn(false)

    // Second callback with changed capabilities
    callback.onCapabilitiesChanged(network, newCaps)

    // Verify cache is updated with new capabilities
    assertEquals(newCaps, connectionStatusProvider.cachedNetworkCapabilities)

    // Verify that subsequent calls use the updated cache
    // Both connection status AND type should change
    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.DISCONNECTED,
      connectionStatusProvider.connectionStatus,
    )
    assertEquals("cellular", connectionStatusProvider.connectionType)

    // Verify observer was notified of the changes (both calls should notify since capabilities
    // changed significantly)
    verify(observer, times(2)).onConnectionStatusChanged(any())
  }

  @Test
  fun `childCallbacks receive network events dispatched by provider`() {
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val mainCallback = connectionStatusProvider.networkCallback
    assertNotNull(mainCallback)

    // Register a mock child callback
    val childCallback = mock<NetworkCallback>()
    AndroidConnectionStatusProvider.getChildCallbacks().add(childCallback)

    // Simulate event on available
    mainCallback.onAvailable(network)

    // Assert child callback received the event
    verify(childCallback).onAvailable(network)

    // Remove it and ensure it no longer receives events
    AndroidConnectionStatusProvider.getChildCallbacks().remove(childCallback)
    mainCallback.onAvailable(network)
    verifyNoMoreInteractions(childCallback)
  }

  @Test
  fun `onForeground notifies child callbacks when disconnected`() {
    val childCallback = mock<NetworkCallback>()
    AndroidConnectionStatusProvider.addNetworkCallback(
      contextMock,
      logger,
      buildInfo,
      childCallback,
    )
    connectionStatusProvider.onBackground()

    // Setup disconnected state
    whenever(connectivityManager.activeNetwork).thenReturn(null)

    connectionStatusProvider.onForeground()

    // Verify child callback was notified of lost connection with any network parameter
    verify(childCallback).onLost(anyOrNull())
  }

  @Test
  fun `close removes AppState listener`() {
    // Clear any setup interactions
    clearInvocations(connectivityManager)

    // Close the provider
    connectionStatusProvider.close()

    // Now test that after closing, the provider no longer responds to lifecycle events
    connectionStatusProvider.onForeground()
    connectionStatusProvider.onBackground()

    assertFalse(
      AppState.getInstance().lifecycleObserver.listeners.any {
        it is SystemEventsBreadcrumbsIntegration
      }
    )
  }

  @Test
  fun `network callbacks work correctly across foreground background transitions`() {
    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    // Get the registered callback
    val callbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    val callback = callbackCaptor.firstValue

    // Simulate network available
    callback.onAvailable(network)

    // Go to background
    connectionStatusProvider.onBackground()

    // Clear the mock to reset interaction count
    clearInvocations(connectivityManager)

    // Go back to foreground
    connectionStatusProvider.onForeground()

    // Verify callback was re-registered
    verify(connectivityManager).registerDefaultNetworkCallback(any())

    // Verify we can still receive network events
    val newCallbackCaptor = argumentCaptor<NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(newCallbackCaptor.capture())
    val newCallback = newCallbackCaptor.lastValue

    // Simulate network capabilities change
    val newCaps = mock<NetworkCapabilities>()
    whenever(newCaps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(newCaps.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(newCaps.hasTransport(TRANSPORT_CELLULAR)).thenReturn(true)

    // First make the network available to set it as current
    newCallback.onAvailable(network)

    // Then change capabilities
    newCallback.onCapabilitiesChanged(network, newCaps)

    // Verify observer was notified (once for onForeground status update, once for capabilities
    // change)
    verify(observer, times(2)).onConnectionStatusChanged(any())
  }

  @Test
  fun `onForeground registers network callback if not already registered`() {
    // First ensure the network callback is not registered (simulate background state)
    connectionStatusProvider.onBackground()

    // Verify callback was unregistered
    assertNull(connectionStatusProvider.networkCallback)

    // Call onForeground
    connectionStatusProvider.onForeground()

    // Verify network callback was registered
    assertNotNull(connectionStatusProvider.networkCallback)
    verify(connectivityManager, times(2)).registerDefaultNetworkCallback(any())
  }

  @Test
  fun `onForeground updates cache and notifies observers`() {
    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    // Simulate going to background first
    connectionStatusProvider.onBackground()

    // Reset mock to clear previous interactions
    whenever(connectivityManager.activeNetwork).thenReturn(network)
    whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(networkCapabilities)
    whenever(networkCapabilities.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(true)
    whenever(networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)).thenReturn(true)
    whenever(networkCapabilities.hasTransport(TRANSPORT_WIFI)).thenReturn(true)

    // Call onForeground
    connectionStatusProvider.onForeground()

    // Verify observer was notified with current status
    verify(observer).onConnectionStatusChanged(IConnectionStatusProvider.ConnectionStatus.CONNECTED)
  }

  @Test
  fun `onForeground does nothing if callback already registered`() {
    // Ensure callback is already registered
    assertNotNull(connectionStatusProvider.networkCallback)
    val initialCallback = connectionStatusProvider.networkCallback

    // Call onForeground
    connectionStatusProvider.onForeground()

    // Verify callback hasn't changed
    assertEquals(initialCallback, connectionStatusProvider.networkCallback)
    // Verify registerDefaultNetworkCallback was only called once (during construction)
    verify(connectivityManager, times(1)).registerDefaultNetworkCallback(any())
  }

  @Test
  fun `onBackground unregisters network callback`() {
    // Ensure callback is registered
    assertNotNull(connectionStatusProvider.networkCallback)

    // Call onBackground
    connectionStatusProvider.onBackground()

    // Verify callback was unregistered
    assertNull(connectionStatusProvider.networkCallback)
    verify(connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
  }

  @Test
  fun `onBackground does not clear observers`() {
    val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    connectionStatusProvider.addConnectionStatusObserver(observer)

    // Call onBackground
    connectionStatusProvider.onBackground()

    // Verify observer is still registered
    assertEquals(1, connectionStatusProvider.statusObservers.size)
    assertTrue(connectionStatusProvider.statusObservers.contains(observer))
  }

  @Test
  fun `onBackground does nothing if callback not registered`() {
    // First unregister by going to background
    connectionStatusProvider.onBackground()
    assertNull(connectionStatusProvider.networkCallback)

    // Reset mock to clear previous interactions
    clearInvocations(connectivityManager)

    // Call onBackground again
    connectionStatusProvider.onBackground()

    // Verify no additional unregister calls
    verifyNoInteractions(connectivityManager)
  }

  @Test
  fun `registerNetworkCallback with a custom handlers calls connectivityManager with it`() {
    val customHandler = object : Handler(Looper.getMainLooper()) {}
    whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
    AndroidConnectionStatusProvider.registerNetworkCallback(
      contextMock,
      logger,
      buildInfo,
      customHandler,
      mock(),
    )

    verify(connectivityManager)
      .registerDefaultNetworkCallback(any<NetworkCallback>(), argThat { this == customHandler })
  }
}
