package io.sentry.android.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryLevel
import io.sentry.android.core.NetworkBreadcrumbsIntegration.NetworkBreadcrumbConnectionDetail
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NetworkBreadcrumbsIntegrationTest {

    private class Fixture {
        val context = mock<Context>()
        var options = SentryAndroidOptions()
        val hub = mock<IHub>()
        val mockBuildInfoProvider = mock<BuildInfoProvider>()
        val connectivityManager = mock<ConnectivityManager>()

        init {
            whenever(mockBuildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
            whenever(context.getSystemService(eq(Context.CONNECTIVITY_SERVICE))).thenReturn(connectivityManager)
        }

        fun getSut(enableNetworkEventBreadcrumbs: Boolean = true, buildInfo: BuildInfoProvider = mockBuildInfoProvider): NetworkBreadcrumbsIntegration {
            options = SentryAndroidOptions().apply {
                isEnableNetworkEventBreadcrumbs = enableNetworkEventBreadcrumbs
            }
            return NetworkBreadcrumbsIntegration(context, buildInfo, options.logger)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When network events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        verify(fixture.connectivityManager).registerDefaultNetworkCallback(any())
        assertNotNull(sut.networkCallback)
    }

    @Test
    fun `When system events breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut(enableNetworkEventBreadcrumbs = false)

        sut.register(fixture.hub, fixture.options)

        verify(fixture.connectivityManager, never()).registerDefaultNetworkCallback(any())
        assertNull(sut.networkCallback)
    }

    @Test
    fun `It doesn't register callback if not on Android N+`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M)
        val sut = fixture.getSut(buildInfo = buildInfo)

        sut.register(fixture.hub, fixture.options)

        verify(fixture.connectivityManager, never()).registerDefaultNetworkCallback(any())
        assertNull(sut.networkCallback)
    }

    @Test
    fun `When NetworkBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        sut.close()

        verify(fixture.connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
        assertNull(sut.networkCallback)
    }

    @Test
    fun `When NetworkBreadcrumbsIntegration is closed, it's ignored if not on Android N+`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M)
        val sut = fixture.getSut(buildInfo = buildInfo)
        assertNull(sut.networkCallback)

        sut.register(fixture.hub, fixture.options)
        sut.close()

        verify(fixture.connectivityManager, never()).unregisterNetworkCallback(any<NetworkCallback>())
        assertNull(sut.networkCallback)
    }

    @Test
    fun `When connected to a new network, a breadcrumb is captured`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        assertNotNull(callback)
        callback.onAvailable(mock())

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("system", it.type)
                assertEquals("network.event", it.category)
                assertEquals(SentryLevel.INFO, it.level)
                assertEquals("networkAvailable", it.data["action"])
            }
        )
    }

    @Test
    fun `When connected to the same network without disconnecting from the previous one, only one breadcrumb is captured`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        assertNotNull(callback)
        val network = mock<Network>()
        callback.onAvailable(network)
        callback.onAvailable(network)

        verify(fixture.hub, times(1)).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When disconnected from a network, a breadcrumb is captured`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        assertNotNull(callback)
        val network = mock<Network>()

        callback.onAvailable(network)
        verify(fixture.hub).addBreadcrumb(any<Breadcrumb>())

        callback.onLost(network)
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("system", it.type)
                assertEquals("network.event", it.category)
                assertEquals(SentryLevel.INFO, it.level)
                assertEquals("networkLost", it.data["action"])
            }
        )
    }

    @Test
    fun `When disconnected from a network, a breadcrumb is captured only if previously connected to that network`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        assertNotNull(callback)
        // callback.onAvailable(network) was not called, so no breadcrumb should be captured
        callback.onLost(mock())
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When a network connection detail changes, a breadcrumb is captured`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        callback.onCapabilitiesChanged(network, mock())
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("system", it.type)
                assertEquals("network.event", it.category)
                assertEquals(SentryLevel.INFO, it.level)
                assertEquals("networkCapabilitiesChanged", it.data["action"])
            },
            any()
        )
    }

    @Test
    fun `When a network connection detail changes, a breadcrumb is captured only if previously connected to that network`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        assertNotNull(callback)
        // callback.onAvailable(network) was not called, so no breadcrumb should be captured
        callback.onCapabilitiesChanged(mock(), mock())
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `When a network connection detail changes, a new breadcrumb is captured if vpn flag changes`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        val details1 = createConnectionDetail(isVpn = false)
        // Not changing the vpn flag doesn't trigger a new breadcrumb
        val details2 = createConnectionDetail(isVpn = false)
        val details3 = createConnectionDetail(isVpn = true)
        callback.onCapabilitiesChanged(network, details1)
        callback.onCapabilitiesChanged(network, details2)
        callback.onCapabilitiesChanged(network, details3)
        inOrder(fixture.hub) {
            verify(fixture.hub).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(false, connectionDetail.isVpn)
                }
            )
            verify(fixture.hub).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(true, connectionDetail.isVpn)
                }
            )
            verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), any())
        }
    }

    @Test
    fun `When a network connection detail changes, a new breadcrumb is captured if type changes`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        val details1 = createConnectionDetail(isWifi = true, isCellular = false)
        // Not changing the connection doesn't trigger a new breadcrumb
        val details2 = createConnectionDetail(isWifi = true, isCellular = false)
        val details3 = createConnectionDetail(isWifi = false, isCellular = true)
        callback.onCapabilitiesChanged(network, details1)
        callback.onCapabilitiesChanged(network, details2)
        callback.onCapabilitiesChanged(network, details3)
        inOrder(fixture.hub) {
            verify(fixture.hub).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals("wifi", connectionDetail.type)
                }
            )
            verify(fixture.hub).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals("cellular", connectionDetail.type)
                }
            )
            verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), any())
        }
    }

    @Test
    fun `When a network connection detail changes, a new breadcrumb is captured if signal strength changes by 5+`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
        val sut = fixture.getSut(buildInfo = buildInfo)
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        val details1 = createConnectionDetail(signalStrength = 50)
        val details2 = createConnectionDetail(signalStrength = 55)
        val details3 = createConnectionDetail(signalStrength = 56)
        callback.onCapabilitiesChanged(network, details1)
        // A change of signal strength of 5 doesn't trigger a new breadcrumb
        callback.onCapabilitiesChanged(network, details2)
        callback.onCapabilitiesChanged(network, details3)
        inOrder(fixture.hub) {
            verify(fixture.hub, times(1)).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(50, connectionDetail.signalStrength)
                }
            )
            verify(fixture.hub, times(1)).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(56, connectionDetail.signalStrength)
                }
            )
            verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), any())
        }
    }

    @Test
    fun `When a network connection detail changes, a new breadcrumb is captured if downBandwidth changes by 1000+ kbps`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
        val sut = fixture.getSut(buildInfo = buildInfo)
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        val details1 = createConnectionDetail(downstreamBandwidthKbps = 1000)
        val details2 = createConnectionDetail(downstreamBandwidthKbps = 2000)
        val details3 = createConnectionDetail(downstreamBandwidthKbps = 2001)
        callback.onCapabilitiesChanged(network, details1)
        // A change of signal strength of 5 doesn't trigger a new breadcrumb
        callback.onCapabilitiesChanged(network, details2)
        callback.onCapabilitiesChanged(network, details3)
        inOrder(fixture.hub) {
            verify(fixture.hub, times(1)).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(1000, connectionDetail.downBandwidth)
                }
            )
            verify(fixture.hub, times(1)).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(2001, connectionDetail.downBandwidth)
                }
            )
            verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), any())
        }
    }

    @Test
    fun `When a network connection detail changes, a new breadcrumb is captured if upBandwidth changes by 1000+ kbps`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
        val sut = fixture.getSut(buildInfo = buildInfo)
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        val details1 = createConnectionDetail(upstreamBandwidthKbps = 1000)
        val details2 = createConnectionDetail(upstreamBandwidthKbps = 2000)
        val details3 = createConnectionDetail(upstreamBandwidthKbps = 2001)
        callback.onCapabilitiesChanged(network, details1)
        // A change of signal strength of 5 doesn't trigger a new breadcrumb
        callback.onCapabilitiesChanged(network, details2)
        callback.onCapabilitiesChanged(network, details3)
        inOrder(fixture.hub) {
            verify(fixture.hub, times(1)).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(1000, connectionDetail.upBandwidth)
                }
            )
            verify(fixture.hub, times(1)).addBreadcrumb(
                any<Breadcrumb>(),
                check {
                    val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                    assertEquals(2001, connectionDetail.upBandwidth)
                }
            )
            verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), any())
        }
    }

    @Test
    fun `signal strength is 0 if not on Android Q+`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        val details1 = createConnectionDetail(signalStrength = 10)
        callback.onCapabilitiesChanged(network, details1)
        verify(fixture.hub).addBreadcrumb(
            any<Breadcrumb>(),
            check {
                val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                assertEquals(0, connectionDetail.signalStrength)
            }
        )
    }

    @Test
    fun `signal strength is 0 if system reports less than -100`() {
        val buildInfo = mock<BuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
        val sut = fixture.getSut(buildInfo = buildInfo)
        sut.register(fixture.hub, fixture.options)
        val callback = sut.networkCallback
        val network = mock<Network>()
        assertNotNull(callback)
        callback.onAvailable(network)
        val details1 = createConnectionDetail(signalStrength = Int.MIN_VALUE)
        callback.onCapabilitiesChanged(network, details1)
        verify(fixture.hub).addBreadcrumb(
            any<Breadcrumb>(),
            check {
                val connectionDetail = it["data"] as NetworkBreadcrumbConnectionDetail
                assertEquals(0, connectionDetail.signalStrength)
            }
        )
    }

    private fun createConnectionDetail(
        downstreamBandwidthKbps: Int = 1000,
        upstreamBandwidthKbps: Int = 1000,
        signalStrength: Int = -50,
        isVpn: Boolean = false,
        isEthernet: Boolean = false,
        isWifi: Boolean = false,
        isCellular: Boolean = false
    ): NetworkCapabilities {
        val capabilities = mock<NetworkCapabilities>()
        whenever(capabilities.linkDownstreamBandwidthKbps).thenReturn(downstreamBandwidthKbps)
        whenever(capabilities.linkUpstreamBandwidthKbps).thenReturn(upstreamBandwidthKbps)
        whenever(capabilities.signalStrength).thenReturn(signalStrength)
        whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(isVpn)
        whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)).thenReturn(isEthernet)
        whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(isWifi)
        whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(isCellular)
        return capabilities
    }
}
