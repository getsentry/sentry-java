package io.sentry.android.core

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
import io.sentry.IConnectionStatusProvider
import io.sentry.ILogger
import io.sentry.SentryOptions
import io.sentry.android.core.internal.util.AndroidConnectionStatusProvider
import io.sentry.test.ImmediateExecutorService
import io.sentry.transport.ICurrentDateProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

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
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M)

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

    connectionStatusProvider =
      AndroidConnectionStatusProvider(contextMock, options, buildInfo, timeProvider)

    // Wait for async callback registration to complete
    connectionStatusProvider.initThread.join()
  }

  @AfterTest
  fun `tear down`() {
    // clear the cache and ensure proper cleanup
    connectionStatusProvider.close()
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

    // Create a new provider with the null connectivity manager
    val providerWithNullConnectivity =
      AndroidConnectionStatusProvider(nullConnectivityContext, options, buildInfo, timeProvider)
    providerWithNullConnectivity.initThread.join() // Wait for async init to complete

    assertEquals(
      IConnectionStatusProvider.ConnectionStatus.UNKNOWN,
      providerWithNullConnectivity.connectionStatus,
    )

    providerWithNullConnectivity.close()
  }

  @Test
  fun `When ConnectivityManager is not available, return null for getConnectionType`() {
    whenever(contextMock.getSystemService(any())).thenReturn(null)
    assertNull(connectionStatusProvider.connectionType)
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
  fun `registerNetworkCallback calls connectivityManager registerDefaultNetworkCallback`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
    whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
    val registered =
      AndroidConnectionStatusProvider.registerNetworkCallback(
        contextMock,
        logger,
        buildInfo,
        mock(),
      )

    assertTrue(registered)
    verify(connectivityManager).registerDefaultNetworkCallback(any())
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

    // Set current network
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
}
