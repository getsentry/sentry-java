package io.sentry.android.core

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkInfo
import android.os.Build
import io.sentry.IConnectionStatusProvider
import io.sentry.android.core.internal.util.AndroidConnectionStatusProvider
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidConnectionStatusProviderTest {

    private lateinit var connectionStatusProvider: AndroidConnectionStatusProvider
    private lateinit var contextMock: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkInfo: NetworkInfo
    private lateinit var buildInfo: BuildInfoProvider
    private lateinit var network: Network
    private lateinit var networkCapabilities: NetworkCapabilities

    @BeforeTest
    fun beforeTest() {
        contextMock = mock()
        connectivityManager = mock()
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)

        networkInfo = mock()
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)

        buildInfo = mock()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M)

        network = mock()
        whenever(connectivityManager.activeNetwork).thenReturn(network)

        networkCapabilities = mock()
        whenever(connectivityManager.getNetworkCapabilities(any())).thenReturn(networkCapabilities)

        connectionStatusProvider = AndroidConnectionStatusProvider(contextMock, mock(), buildInfo)
    }

    @Test
    fun `When network is active and connected with permission, return CONNECTED for isConnected`() {
        whenever(networkInfo.isConnected).thenReturn(true)
        assertEquals(
            IConnectionStatusProvider.ConnectionStatus.CONNECTED,
            connectionStatusProvider.connectionStatus
        )
    }

    @Test
    fun `When network is active but not connected with permission, return DISCONNECTED for isConnected`() {
        whenever(networkInfo.isConnected).thenReturn(false)

        assertEquals(
            IConnectionStatusProvider.ConnectionStatus.DISCONNECTED,
            connectionStatusProvider.connectionStatus
        )
    }

    @Test
    fun `When there's no permission, return NO_PERMISSION for isConnected`() {
        whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)

        assertEquals(
            IConnectionStatusProvider.ConnectionStatus.NO_PERMISSION,
            connectionStatusProvider.connectionStatus
        )
    }

    @Test
    fun `When network is not active, return DISCONNECTED for isConnected`() {
        assertEquals(
            IConnectionStatusProvider.ConnectionStatus.DISCONNECTED,
            connectionStatusProvider.connectionStatus
        )
    }

    @Test
    fun `When ConnectivityManager is not available, return UNKNOWN for isConnected`() {
        whenever(contextMock.getSystemService(any())).thenReturn(null)
        assertEquals(
            IConnectionStatusProvider.ConnectionStatus.UNKNOWN,
            connectionStatusProvider.connectionStatus
        )
    }

    @Test
    fun `When ConnectivityManager is not available, return null for getConnectionType`() {
        assertNull(AndroidConnectionStatusProvider.getConnectionType(mock(), mock(), buildInfo))
    }

    @Test
    fun `When there's no permission, return null for getConnectionType`() {
        whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)

        assertNull(AndroidConnectionStatusProvider.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network is not active, return null for getConnectionType`() {
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)

        assertNull(AndroidConnectionStatusProvider.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network capabilities are not available, return null for getConnectionType`() {
        assertNull(AndroidConnectionStatusProvider.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network capabilities has TRANSPORT_WIFI, return wifi`() {
        whenever(networkCapabilities.hasTransport(eq(TRANSPORT_WIFI))).thenReturn(true)

        assertEquals("wifi", AndroidConnectionStatusProvider.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network capabilities has TRANSPORT_ETHERNET, return ethernet`() {
        whenever(networkCapabilities.hasTransport(eq(TRANSPORT_ETHERNET))).thenReturn(true)

        assertEquals(
            "ethernet",
            AndroidConnectionStatusProvider.getConnectionType(contextMock, mock(), buildInfo)
        )
    }

    @Test
    fun `When network capabilities has TRANSPORT_CELLULAR, return cellular`() {
        whenever(networkCapabilities.hasTransport(eq(TRANSPORT_CELLULAR))).thenReturn(true)

        assertEquals(
            "cellular",
            AndroidConnectionStatusProvider.getConnectionType(contextMock, mock(), buildInfo)
        )
    }

    @Test
    fun `When there's no permission, do not register any NetworkCallback`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)
        val registered =
            AndroidConnectionStatusProvider.registerNetworkCallback(contextMock, mock(), buildInfo, mock())

        assertFalse(registered)
        verify(connectivityManager, never()).registerDefaultNetworkCallback(any())
    }

    @Test
    fun `When sdkInfoVersion is not min N, do not register any NetworkCallback`() {
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        val registered =
            AndroidConnectionStatusProvider.registerNetworkCallback(contextMock, mock(), buildInfo, mock())

        assertFalse(registered)
        verify(connectivityManager, never()).registerDefaultNetworkCallback(any())
    }

    @Test
    fun `registerNetworkCallback calls connectivityManager registerDefaultNetworkCallback`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        val registered =
            AndroidConnectionStatusProvider.registerNetworkCallback(contextMock, mock(), buildInfo, mock())

        assertTrue(registered)
        verify(connectivityManager).registerDefaultNetworkCallback(any())
    }

    @Test
    fun `unregisterNetworkCallback calls connectivityManager unregisterDefaultNetworkCallback`() {
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        AndroidConnectionStatusProvider.unregisterNetworkCallback(contextMock, mock(), mock())

        verify(connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
    }

    @Test
    fun `When connectivityManager getActiveNetwork throws an exception, getConnectionType returns null`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.S)
        whenever(connectivityManager.activeNetwork).thenThrow(SecurityException("Android OS Bug"))

        assertNull(AndroidConnectionStatusProvider.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When connectivityManager registerDefaultCallback throws an exception, false is returned`() {
        whenever(connectivityManager.registerDefaultNetworkCallback(any())).thenThrow(
            SecurityException("Android OS Bug")
        )
        assertFalse(
            AndroidConnectionStatusProvider.registerNetworkCallback(
                contextMock,
                mock(),
                buildInfo,
                mock()
            )
        )
    }

    @Test
    fun `When connectivityManager unregisterDefaultCallback throws an exception, it gets swallowed`() {
        whenever(connectivityManager.registerDefaultNetworkCallback(any())).thenThrow(
            SecurityException("Android OS Bug")
        )

        var failed = false
        try {
            AndroidConnectionStatusProvider.unregisterNetworkCallback(contextMock, mock(), mock())
        } catch (t: Throwable) {
            failed = true
        }
        assertFalse(failed)
    }

    @Test
    fun `connectionStatus returns NO_PERMISSIONS when context does not hold the permission`() {
        whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)
        assertEquals(IConnectionStatusProvider.ConnectionStatus.NO_PERMISSION, connectionStatusProvider.connectionStatus)
    }

    @Test
    fun `connectionStatus returns ethernet when underlying mechanism provides ethernet`() {
        whenever(networkCapabilities.hasTransport(eq(TRANSPORT_ETHERNET))).thenReturn(true)
        assertEquals(
            "ethernet",
            connectionStatusProvider.connectionType
        )
    }

    @Test
    fun `adding and removing an observer works correctly`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

        val observer = IConnectionStatusProvider.IConnectionStatusObserver { }
        val addResult = connectionStatusProvider.addConnectionStatusObserver(observer)
        assertTrue(addResult)

        connectionStatusProvider.removeConnectionStatusObserver(observer)
        assertTrue(connectionStatusProvider.registeredCallbacks.isEmpty())
    }

    @Test
    fun `underlying callbacks correctly trigger update`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

        var callback: NetworkCallback? = null
        whenever(connectivityManager.registerDefaultNetworkCallback(any())).then { invocation ->
            callback = invocation.getArgument(0, NetworkCallback::class.java)
            Unit
        }
        val observer = mock<IConnectionStatusProvider.IConnectionStatusObserver>()
        connectionStatusProvider.addConnectionStatusObserver(observer)
        callback!!.onAvailable(mock<Network>())
        callback!!.onUnavailable()
        callback!!.onLosing(mock<Network>(), 0)
        callback!!.onLost(mock<Network>())
        callback!!.onUnavailable()
        connectionStatusProvider.removeConnectionStatusObserver(observer)

        verify(observer, times(5)).onConnectionStatusChanged(any())
    }
}
