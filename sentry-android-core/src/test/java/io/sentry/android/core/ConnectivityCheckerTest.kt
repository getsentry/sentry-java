package io.sentry.android.core

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.ConnectivityManager.TYPE_ETHERNET
import android.net.ConnectivityManager.TYPE_MOBILE
import android.net.ConnectivityManager.TYPE_WIFI
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkInfo
import android.os.Build
import io.sentry.android.core.internal.util.ConnectivityChecker
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectivityCheckerTest {

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
    }

    @Test
    fun `When network is active and connected with permission, return CONNECTED for isConnected`() {
        whenever(networkInfo.isConnected).thenReturn(true)
        assertEquals(
            ConnectivityChecker.Status.CONNECTED,
            ConnectivityChecker.getConnectionStatus(contextMock, mock())
        )
    }

    @Test
    fun `When network is active but not connected with permission, return NOT_CONNECTED for isConnected`() {
        whenever(networkInfo.isConnected).thenReturn(false)

        assertEquals(
            ConnectivityChecker.Status.NOT_CONNECTED,
            ConnectivityChecker.getConnectionStatus(contextMock, mock())
        )
    }

    @Test
    fun `When there's no permission, return NO_PERMISSION for isConnected`() {
        whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)

        assertEquals(
            ConnectivityChecker.Status.NO_PERMISSION,
            ConnectivityChecker.getConnectionStatus(contextMock, mock())
        )
    }

    @Test
    fun `When network is not active, return NOT_CONNECTED for isConnected`() {
        assertEquals(
            ConnectivityChecker.Status.NOT_CONNECTED,
            ConnectivityChecker.getConnectionStatus(contextMock, mock())
        )
    }

    @Test
    fun `When ConnectivityManager is not available, return UNKNOWN for isConnected`() {
        assertEquals(
            ConnectivityChecker.Status.UNKNOWN,
            ConnectivityChecker.getConnectionStatus(mock(), mock())
        )
    }

    @Test
    fun `When ConnectivityManager is not available, return null for getConnectionType`() {
        assertNull(ConnectivityChecker.getConnectionType(mock(), mock(), buildInfo))
    }

    @Test
    fun `When sdkInfoVersion is not min Marshmallow, return null for getConnectionType`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.ICE_CREAM_SANDWICH)

        assertNull(ConnectivityChecker.getConnectionType(mock(), mock(), buildInfo))
    }

    @Test
    fun `When there's no permission, return null for getConnectionType`() {
        whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)

        assertNull(ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network is not active, return null for getConnectionType`() {
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)

        assertNull(ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network capabilities are not available, return null for getConnectionType`() {
        assertNull(ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network capabilities has TRANSPORT_WIFI, return wifi`() {
        whenever(networkCapabilities.hasTransport(eq(TRANSPORT_WIFI))).thenReturn(true)

        assertEquals("wifi", ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network is TYPE_WIFI, return wifi`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        whenever(networkInfo.type).thenReturn(TYPE_WIFI)

        assertEquals("wifi", ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When network capabilities has TRANSPORT_ETHERNET, return ethernet`() {
        whenever(networkCapabilities.hasTransport(eq(TRANSPORT_ETHERNET))).thenReturn(true)

        assertEquals(
            "ethernet",
            ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo)
        )
    }

    @Test
    fun `When network is TYPE_ETHERNET, return ethernet`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        whenever(networkInfo.type).thenReturn(TYPE_ETHERNET)

        assertEquals(
            "ethernet",
            ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo)
        )
    }

    @Test
    fun `When network capabilities has TRANSPORT_CELLULAR, return cellular`() {
        whenever(networkCapabilities.hasTransport(eq(TRANSPORT_CELLULAR))).thenReturn(true)

        assertEquals(
            "cellular",
            ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo)
        )
    }

    @Test
    fun `When network is TYPE_MOBILE, return cellular`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        whenever(networkInfo.type).thenReturn(TYPE_MOBILE)

        assertEquals(
            "cellular",
            ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo)
        )
    }

    @Test
    fun `When there's no permission, do not register any NetworkCallback`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        whenever(contextMock.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)
        val registered =
            ConnectivityChecker.registerNetworkCallback(contextMock, mock(), buildInfo, mock())

        assertFalse(registered)
        verify(connectivityManager, never()).registerDefaultNetworkCallback(any())
    }

    @Test
    fun `When sdkInfoVersion is not min N, do not register any NetworkCallback`() {
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        val registered =
            ConnectivityChecker.registerNetworkCallback(contextMock, mock(), buildInfo, mock())

        assertFalse(registered)
        verify(connectivityManager, never()).registerDefaultNetworkCallback(any())
    }

    @Test
    fun `registerNetworkCallback calls connectivityManager registerDefaultNetworkCallback`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        val registered =
            ConnectivityChecker.registerNetworkCallback(contextMock, mock(), buildInfo, mock())

        assertTrue(registered)
        verify(connectivityManager).registerDefaultNetworkCallback(any())
    }

    @Test
    fun `When sdkInfoVersion is not min Lollipop, do not unregister any NetworkCallback`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.KITKAT)
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        ConnectivityChecker.unregisterNetworkCallback(contextMock, mock(), buildInfo, mock())

        verify(connectivityManager, never()).unregisterNetworkCallback(any<NetworkCallback>())
    }

    @Test
    fun `unregisterNetworkCallback calls connectivityManager unregisterDefaultNetworkCallback`() {
        whenever(contextMock.getSystemService(any())).thenReturn(connectivityManager)
        ConnectivityChecker.unregisterNetworkCallback(contextMock, mock(), buildInfo, mock())

        verify(connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
    }

    @Test
    fun `When connectivityManager getActiveNetwork throws an exception, getConnectionType returns null`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.S)
        whenever(connectivityManager.activeNetwork).thenThrow(SecurityException("Android OS Bug"))

        assertNull(ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo))
    }

    @Test
    fun `When connectivityManager getActiveNetworkInfo throws an exception, getConnectionType returns null`() {
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.KITKAT)
        whenever(connectivityManager.activeNetworkInfo).thenThrow(SecurityException("Android OS Bug"))

        assertNull(ConnectivityChecker.getConnectionType(contextMock, mock(), buildInfo))
        assertEquals(ConnectivityChecker.Status.UNKNOWN, ConnectivityChecker.getConnectionStatus(contextMock, mock()))
    }

    @Test
    fun `When connectivityManager registerDefaultCallback throws an exception, false is returned`() {
        whenever(connectivityManager.registerDefaultNetworkCallback(any())).thenThrow(
            SecurityException("Android OS Bug")
        )
        assertFalse(
            ConnectivityChecker.registerNetworkCallback(
                contextMock,
                mock(),
                buildInfo,
                mock()
            )
        )
    }

    @Test
    fun `When connectivityManager unregisterDefaultCallback throws an exception, false is returned`() {
        whenever(connectivityManager.registerDefaultNetworkCallback(any())).thenThrow(
            SecurityException("Android OS Bug")
        )

        var failed = false
        try {
            ConnectivityChecker.unregisterNetworkCallback(contextMock, mock(), buildInfo, mock())
        } catch (t: Throwable) {
            failed = true
        }
        assertFalse(failed)
    }
}
