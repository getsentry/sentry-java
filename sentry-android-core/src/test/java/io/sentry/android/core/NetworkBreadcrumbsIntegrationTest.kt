package io.sentry.android.core

import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SentryDateProvider
import io.sentry.SentryLevel
import io.sentry.SentryNanotimeDate
import io.sentry.TypeCheckHint
import io.sentry.android.core.NetworkBreadcrumbsIntegration.NetworkBreadcrumbConnectionDetail
import io.sentry.android.core.NetworkBreadcrumbsIntegration.NetworkBreadcrumbsNetworkCallback
import io.sentry.android.core.internal.util.AndroidConnectionStatusProvider
import io.sentry.test.ImmediateExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.KInOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NetworkBreadcrumbsIntegrationTest {
  private class Fixture {
    val context = mock<Context>()
    var options = SentryAndroidOptions()
    val scopes = mock<IScopes>()
    val mockBuildInfoProvider = mock<BuildInfoProvider>()
    var nowMs: Long = 0
    val network = mock<Network>()

    fun getSut(
      enableNetworkEventBreadcrumbs: Boolean = true,
      buildInfo: BuildInfoProvider = mockBuildInfoProvider,
      executor: ISentryExecutorService = ImmediateExecutorService(),
    ): NetworkBreadcrumbsIntegration {
      options =
        SentryAndroidOptions().apply {
          executorService = executor
          isEnableNetworkEventBreadcrumbs = enableNetworkEventBreadcrumbs
          dateProvider = SentryDateProvider {
            val nowNanos = TimeUnit.MILLISECONDS.toNanos(nowMs ?: System.currentTimeMillis())
            SentryNanotimeDate(DateUtils.nanosToDate(nowNanos), nowNanos)
          }
        }
      return NetworkBreadcrumbsIntegration(context, buildInfo)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    whenever(fixture.mockBuildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)
  }

  @AfterTest
  fun `tear down`() {
    AndroidConnectionStatusProvider.getChildCallbacks().clear()
  }

  @Test
  fun `When network events breadcrumb is enabled, it registers callback`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertFalse(AndroidConnectionStatusProvider.getChildCallbacks().isEmpty())
    assertNotNull(sut.networkCallback)
  }

  @Test
  fun `When system events breadcrumb is disabled, it doesn't register callback`() {
    val sut = fixture.getSut(enableNetworkEventBreadcrumbs = false)

    sut.register(fixture.scopes, fixture.options)

    assertTrue(AndroidConnectionStatusProvider.getChildCallbacks().isEmpty())
    assertNull(sut.networkCallback)
  }

  @Test
  fun `It doesn't register callback if not on Android N+`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.M)
    val sut = fixture.getSut(buildInfo = buildInfo)

    sut.register(fixture.scopes, fixture.options)

    assertTrue(AndroidConnectionStatusProvider.getChildCallbacks().isEmpty())
    assertNull(sut.networkCallback)
  }

  @Test
  fun `When NetworkBreadcrumbsIntegration is closed, it should unregister the callback`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    sut.close()

    assertTrue(AndroidConnectionStatusProvider.getChildCallbacks().isEmpty())
    assertNull(sut.networkCallback)
  }

  @Test
  fun `When connected to a new network, a breadcrumb is captured`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(mock())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("system", it.type)
          assertEquals("network.event", it.category)
          assertEquals(SentryLevel.INFO, it.level)
          assertEquals("NETWORK_AVAILABLE", it.data["action"])
        }
      )
  }

  @Test
  fun `When disconnected from a network, a breadcrumb is captured`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)

    callback.onAvailable(fixture.network)
    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>())

    callback.onLost(fixture.network)
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("system", it.type)
          assertEquals("network.event", it.category)
          assertEquals(SentryLevel.INFO, it.level)
          assertEquals("NETWORK_LOST", it.data["action"])
        }
      )
  }

  @Test
  fun `When a network connection detail changes, a breadcrumb is captured`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
    val sut = fixture.getSut(buildInfo = buildInfo)
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    onCapabilitiesChanged(
      callback,
      createConnectionDetail(
        downstreamBandwidthKbps = 1000,
        upstreamBandwidthKbps = 500,
        signalStrength = -50,
        isVpn = true,
        isEthernet = false,
        isWifi = true,
        isCellular = false,
      ),
    )
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("system", it.type)
          assertEquals("network.event", it.category)
          assertEquals(SentryLevel.INFO, it.level)
          assertEquals("NETWORK_CAPABILITIES_CHANGED", it.data["action"])
          assertEquals(1000, it.data["download_bandwidth"])
          assertEquals(500, it.data["upload_bandwidth"])
          assertTrue(it.data["vpn_active"] as Boolean)
          assertEquals("wifi", it.data["network_type"])
          assertEquals(-50, it.data["signal_strength"])
        },
        any(),
      )
  }

  @Test
  fun `When a network connection detail changes, a new breadcrumb is captured if vpn flag changes`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(isVpn = false)
    // Not changing the vpn flag doesn't trigger a new breadcrumb
    val details2 = createConnectionDetail(isVpn = false)
    val details3 = createConnectionDetail(isVpn = true)
    onCapabilitiesChanged(callback, details1)
    onCapabilitiesChanged(callback, details2)
    onCapabilitiesChanged(callback, details3)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertFalse(it.isVpn) }
      verifyBreadcrumbInOrder { assertTrue(it.isVpn) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `When a network connection detail changes, a new breadcrumb is captured if type changes`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(isWifi = true, isCellular = false)
    // Not changing the connection doesn't trigger a new breadcrumb
    val details2 = createConnectionDetail(isWifi = true, isCellular = false)
    val details3 = createConnectionDetail(isWifi = false, isCellular = true)
    onCapabilitiesChanged(callback, details1)
    onCapabilitiesChanged(callback, details2)
    onCapabilitiesChanged(callback, details3)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals("wifi", it.type) }
      verifyBreadcrumbInOrder { assertEquals("cellular", it.type) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `When a network connection detail changes, a new breadcrumb is captured if signal strength changes by 5+`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
    val sut = fixture.getSut(buildInfo = buildInfo)
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(signalStrength = 50)
    val details2 = createConnectionDetail(signalStrength = 55)
    val details3 = createConnectionDetail(signalStrength = 56)
    onCapabilitiesChanged(callback, details1)
    // A change of signal strength of 5 doesn't trigger a new breadcrumb
    onCapabilitiesChanged(callback, details2)
    onCapabilitiesChanged(callback, details3)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals(50, it.signalStrength) }
      verifyBreadcrumbInOrder { assertEquals(56, it.signalStrength) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `When a network connection detail changes, a new breadcrumb is captured if downBandwidth changes by 1000+ kbps or 10 percent`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
    val sut = fixture.getSut(buildInfo = buildInfo)
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(downstreamBandwidthKbps = 1000)
    val details2 = createConnectionDetail(downstreamBandwidthKbps = 2000)
    val details3 = createConnectionDetail(downstreamBandwidthKbps = 20000)
    val details4 = createConnectionDetail(downstreamBandwidthKbps = 22000)
    val details5 = createConnectionDetail(downstreamBandwidthKbps = 22001)
    onCapabilitiesChanged(callback, details1)
    // A change of download bandwidth of 1000 doesn't trigger a new breadcrumb
    onCapabilitiesChanged(callback, details2)
    onCapabilitiesChanged(callback, details3)
    // A change of download bandwidth of 10% (more than 1000) doesn't trigger a new breadcrumb
    onCapabilitiesChanged(callback, details4)
    onCapabilitiesChanged(callback, details5)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals(1000, it.downBandwidth) }
      verifyBreadcrumbInOrder { assertEquals(20000, it.downBandwidth) }
      verifyBreadcrumbInOrder { assertEquals(22001, it.downBandwidth) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `When a network connection detail changes, a new breadcrumb is captured if upBandwidth changes by 1000+ kbps`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
    val sut = fixture.getSut(buildInfo = buildInfo)
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(upstreamBandwidthKbps = 1000)
    val details2 = createConnectionDetail(upstreamBandwidthKbps = 2000)
    val details3 = createConnectionDetail(upstreamBandwidthKbps = 20000)
    val details4 = createConnectionDetail(upstreamBandwidthKbps = 22000)
    val details5 = createConnectionDetail(upstreamBandwidthKbps = 22001)
    onCapabilitiesChanged(callback, details1)
    // A change of upload bandwidth of 1000 doesn't trigger a new breadcrumb
    onCapabilitiesChanged(callback, details2)
    onCapabilitiesChanged(callback, details3)
    // A change of upload bandwidth of 10% (more than 1000) doesn't trigger a new breadcrumb
    onCapabilitiesChanged(callback, details4)
    onCapabilitiesChanged(callback, details5)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals(1000, it.upBandwidth) }
      verifyBreadcrumbInOrder { assertEquals(20000, it.upBandwidth) }
      verifyBreadcrumbInOrder { assertEquals(22001, it.upBandwidth) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `signal strength is 0 if not on Android Q+`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(signalStrength = 10)
    onCapabilitiesChanged(callback, details1)
    verifyBreadcrumb { assertEquals(0, it.signalStrength) }
  }

  @Test
  fun `signal strength is 0 if system reports less than -100`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
    val sut = fixture.getSut(buildInfo = buildInfo)
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(signalStrength = Int.MIN_VALUE)
    onCapabilitiesChanged(callback, details1)

    verifyBreadcrumb { assertEquals(0, it.signalStrength) }
  }

  @Test
  fun `A breadcrumb is captured when vpn status changes, regardless of the timestamp`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(isVpn = false)
    val details2 = createConnectionDetail(isVpn = true)
    onCapabilitiesChanged(callback, details1, 0)
    onCapabilitiesChanged(callback, details2, 0)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertFalse(it.isVpn) }
      verifyBreadcrumbInOrder { assertTrue(it.isVpn) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `A breadcrumb is captured when connection type changes, regardless of the timestamp`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(isWifi = true, isCellular = false, isEthernet = false)
    val details2 = createConnectionDetail(isWifi = false, isCellular = true, isEthernet = false)
    val details3 = createConnectionDetail(isWifi = false, isCellular = false, isEthernet = true)
    onCapabilitiesChanged(callback, details1, 0)
    onCapabilitiesChanged(callback, details2, 0)
    onCapabilitiesChanged(callback, details3, 0)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals("wifi", it.type) }
      verifyBreadcrumbInOrder { assertEquals("cellular", it.type) }
      verifyBreadcrumbInOrder { assertEquals("ethernet", it.type) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `A breadcrumb is captured when signal strength changes at most once every 5 seconds`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
    val sut = fixture.getSut(buildInfo = buildInfo)
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(signalStrength = 1)
    val details2 = createConnectionDetail(signalStrength = 50)
    val details3 = createConnectionDetail(signalStrength = 51)
    onCapabilitiesChanged(callback, details1, 0)
    onCapabilitiesChanged(callback, details2, 0)
    onCapabilitiesChanged(callback, details3, 5000)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals(1, it.signalStrength) }
      verifyBreadcrumbInOrder { assertEquals(51, it.signalStrength) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `A breadcrumb is captured when downBandwidth changes at most once every 5 seconds`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(downstreamBandwidthKbps = 1)
    val details2 = createConnectionDetail(downstreamBandwidthKbps = 2000)
    val details3 = createConnectionDetail(downstreamBandwidthKbps = 2001)
    onCapabilitiesChanged(callback, details1, 0)
    onCapabilitiesChanged(callback, details2, 0)
    onCapabilitiesChanged(callback, details3, 5000)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals(1, it.downBandwidth) }
      verifyBreadcrumbInOrder { assertEquals(2001, it.downBandwidth) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  @Test
  fun `A breadcrumb is captured when upBandwidth changes at most once every 5 seconds`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    val callback = sut.networkCallback
    assertNotNull(callback)
    callback.onAvailable(fixture.network)
    val details1 = createConnectionDetail(upstreamBandwidthKbps = 1)
    val details2 = createConnectionDetail(upstreamBandwidthKbps = 2000)
    val details3 = createConnectionDetail(upstreamBandwidthKbps = 2001)
    onCapabilitiesChanged(callback, details1, 0)
    onCapabilitiesChanged(callback, details2, 0)
    onCapabilitiesChanged(callback, details3, 5000)
    inOrder(fixture.scopes) {
      verifyBreadcrumbInOrder { assertEquals(1, it.upBandwidth) }
      verifyBreadcrumbInOrder { assertEquals(2001, it.upBandwidth) }
      verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>(), any())
    }
  }

  private fun KInOrder.verifyBreadcrumbInOrder(
    check: (detail: NetworkBreadcrumbConnectionDetail) -> Unit
  ) {
    verify(fixture.scopes, times(1))
      .addBreadcrumb(
        any<Breadcrumb>(),
        check {
          val connectionDetail =
            it[TypeCheckHint.ANDROID_NETWORK_CAPABILITIES] as NetworkBreadcrumbConnectionDetail
          check(connectionDetail)
        },
      )
  }

  private fun verifyBreadcrumb(check: (detail: NetworkBreadcrumbConnectionDetail) -> Unit) {
    verify(fixture.scopes)
      .addBreadcrumb(
        any<Breadcrumb>(),
        check {
          val connectionDetail =
            it[TypeCheckHint.ANDROID_NETWORK_CAPABILITIES] as NetworkBreadcrumbConnectionDetail
          check(connectionDetail)
        },
      )
  }

  private fun createConnectionDetail(
    downstreamBandwidthKbps: Int = 1000,
    upstreamBandwidthKbps: Int = 1000,
    signalStrength: Int = -50,
    isVpn: Boolean = false,
    isEthernet: Boolean = false,
    isWifi: Boolean = false,
    isCellular: Boolean = false,
  ): NetworkCapabilities {
    val capabilities = mock<NetworkCapabilities>()
    whenever(capabilities.linkDownstreamBandwidthKbps).thenReturn(downstreamBandwidthKbps)
    whenever(capabilities.linkUpstreamBandwidthKbps).thenReturn(upstreamBandwidthKbps)
    whenever(capabilities.signalStrength).thenReturn(signalStrength)
    whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(isVpn)
    whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
      .thenReturn(isEthernet)
    whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(isWifi)
    whenever(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
      .thenReturn(isCellular)
    return capabilities
  }

  private fun onCapabilitiesChanged(
    callback: NetworkBreadcrumbsNetworkCallback,
    capabilities: NetworkCapabilities,
    advanceTimeMs: Long = 5000L,
  ) {
    fixture.nowMs += advanceTimeMs
    callback.onCapabilitiesChanged(fixture.network, capabilities)
  }
}
