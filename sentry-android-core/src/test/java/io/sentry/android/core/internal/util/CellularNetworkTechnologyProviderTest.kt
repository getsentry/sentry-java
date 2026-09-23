package io.sentry.android.core.internal.util

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.android.core.BuildInfoProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.S])
class CellularNetworkTechnologyProviderTest {
  private class Fixture {
    val context = mock<Context>()
    val logger = mock<ILogger>()
    val buildInfo = mock<BuildInfoProvider>()
    val telephonyManager = mock<TelephonyManager>()

    /** Runs the display info callback inline, so tests don't have to wait for another thread. */
    val executor = Executor { it.run() }

    fun getSut(
      sdkVersion: Int = Build.VERSION_CODES.S,
      hasPermission: Boolean = false,
      hasTelephonyManager: Boolean = true,
    ): CellularNetworkTechnologyProvider {
      whenever(buildInfo.sdkInfoVersion).thenReturn(sdkVersion)
      whenever(context.getSystemService(eq(Context.TELEPHONY_SERVICE)))
        .thenReturn(if (hasTelephonyManager) telephonyManager else null)
      whenever(context.checkPermission(any(), any(), any()))
        .thenReturn(if (hasPermission) PERMISSION_GRANTED else PERMISSION_DENIED)
      return CellularNetworkTechnologyProvider(context, logger, buildInfo, executor)
    }

    fun displayInfo(networkType: Int, overrideNetworkType: Int): TelephonyDisplayInfo {
      val displayInfo = mock<TelephonyDisplayInfo>()
      whenever(displayInfo.networkType).thenReturn(networkType)
      whenever(displayInfo.overrideNetworkType).thenReturn(overrideNetworkType)
      return displayInfo
    }

    /** Registers the provider and returns the callback the telephony manager received. */
    fun registerAndCaptureCallback(
      provider: CellularNetworkTechnologyProvider
    ): TelephonyCallback.DisplayInfoListener {
      provider.register()
      val captor = argumentCaptor<TelephonyCallback>()
      verify(telephonyManager).registerTelephonyCallback(eq(executor), captor.capture())
      return captor.firstValue as TelephonyCallback.DisplayInfoListener
    }
  }

  private lateinit var fixture: Fixture

  @BeforeTest
  fun beforeTest() {
    fixture = Fixture()
  }

  @Test
  fun `networkTypeToGeneration maps second generation network types`() {
    val networkTypes =
      listOf(
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM,
      )

    networkTypes.forEach { networkType ->
      assertThat(CellularNetworkTechnologyProvider.networkTypeToGeneration(networkType))
        .isEqualTo("2g")
    }
  }

  @Test
  fun `networkTypeToGeneration maps third generation network types`() {
    val networkTypes =
      listOf(
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA,
      )

    networkTypes.forEach { networkType ->
      assertThat(CellularNetworkTechnologyProvider.networkTypeToGeneration(networkType))
        .isEqualTo("3g")
    }
  }

  @Test
  fun `networkTypeToGeneration maps fourth and fifth generation network types`() {
    assertThat(
        CellularNetworkTechnologyProvider.networkTypeToGeneration(TelephonyManager.NETWORK_TYPE_LTE)
      )
      .isEqualTo("4g")
    assertThat(
        CellularNetworkTechnologyProvider.networkTypeToGeneration(
          TelephonyManager.NETWORK_TYPE_IWLAN
        )
      )
      .isEqualTo("4g")
    assertThat(
        CellularNetworkTechnologyProvider.networkTypeToGeneration(TelephonyManager.NETWORK_TYPE_NR)
      )
      .isEqualTo("5g")
  }

  @Test
  fun `networkTypeToGeneration returns null for unknown network types`() {
    assertThat(
        CellularNetworkTechnologyProvider.networkTypeToGeneration(
          TelephonyManager.NETWORK_TYPE_UNKNOWN
        )
      )
      .isNull()
  }

  @Test
  fun `toGeneration prefers the override network type`() {
    val displayInfo =
      fixture.displayInfo(
        networkType = TelephonyManager.NETWORK_TYPE_LTE,
        overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
      )

    assertThat(CellularNetworkTechnologyProvider.toGeneration(displayInfo)).isEqualTo("5g")
  }

  @Test
  fun `toGeneration falls back to the network type without an override`() {
    val displayInfo =
      fixture.displayInfo(
        networkType = TelephonyManager.NETWORK_TYPE_LTE,
        overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
      )

    assertThat(CellularNetworkTechnologyProvider.toGeneration(displayInfo)).isEqualTo("4g")
  }

  @Test
  fun `on Android 12 and above the technology comes from the display info listener`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.S)
    val listener = fixture.registerAndCaptureCallback(provider)

    assertThat(provider.cellularNetworkTechnology).isNull()

    listener.onDisplayInfoChanged(
      fixture.displayInfo(
        networkType = TelephonyManager.NETWORK_TYPE_NR,
        overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
      )
    )

    assertThat(provider.cellularNetworkTechnology).isEqualTo("5g")
  }

  @Test
  fun `on Android 12 and above the technology is read without any permission`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.S, hasPermission = false)
    val listener = fixture.registerAndCaptureCallback(provider)

    listener.onDisplayInfoChanged(
      fixture.displayInfo(
        networkType = TelephonyManager.NETWORK_TYPE_LTE,
        overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
      )
    )

    assertThat(provider.cellularNetworkTechnology).isEqualTo("4g")
    verify(fixture.telephonyManager, never()).dataNetworkType
  }

  @Test
  fun `registering twice only registers one callback`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.S)

    provider.register()
    provider.register()

    verify(fixture.telephonyManager).registerTelephonyCallback(eq(fixture.executor), any())
  }

  @Test
  fun `concurrent registration only registers one callback`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.S)
    val threadCount = 8
    val start = CountDownLatch(1)
    val done = CountDownLatch(threadCount)
    repeat(threadCount) {
      Thread {
          start.await()
          provider.register()
          done.countDown()
        }
        .start()
    }

    start.countDown()
    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()

    verify(fixture.telephonyManager).registerTelephonyCallback(eq(fixture.executor), any())
  }

  @Test
  fun `unregistering stops the listener and forgets the technology`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.S)
    val listener = fixture.registerAndCaptureCallback(provider)
    listener.onDisplayInfoChanged(
      fixture.displayInfo(
        networkType = TelephonyManager.NETWORK_TYPE_LTE,
        overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
      )
    )

    provider.unregister()

    verify(fixture.telephonyManager).unregisterTelephonyCallback(listener as TelephonyCallback)
    assertThat(provider.cellularNetworkTechnology).isNull()
  }

  @Test
  fun `unregistering without registering does nothing`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.S)

    provider.unregister()

    verifyNoInteractions(fixture.telephonyManager)
  }

  @Test
  fun `below Android 12 the technology is not read without permission`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.R, hasPermission = false)

    assertThat(provider.cellularNetworkTechnology).isNull()
    verify(fixture.telephonyManager, never()).dataNetworkType
  }

  @Test
  fun `below Android 12 the technology is read with permission`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.R, hasPermission = true)
    whenever(fixture.telephonyManager.dataNetworkType).thenReturn(TelephonyManager.NETWORK_TYPE_LTE)

    assertThat(provider.cellularNetworkTechnology).isEqualTo("4g")
  }

  @Test
  fun `below Android 12 a security exception is swallowed`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.R, hasPermission = true)
    whenever(fixture.telephonyManager.dataNetworkType).thenThrow(SecurityException("denied"))

    assertThat(provider.cellularNetworkTechnology).isNull()
  }

  @Test
  fun `below Android 7 the technology is not read because getDataNetworkType is unavailable`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.M, hasPermission = true)

    assertThat(provider.cellularNetworkTechnology).isNull()
    verify(fixture.telephonyManager, never()).dataNetworkType
  }

  @Test
  fun `below Android 12 registering does nothing`() {
    val provider = fixture.getSut(sdkVersion = Build.VERSION_CODES.R)

    provider.register()

    verifyNoInteractions(fixture.telephonyManager)
  }

  @Test
  fun `without a telephony manager no technology is reported`() {
    val provider =
      fixture.getSut(
        sdkVersion = Build.VERSION_CODES.R,
        hasPermission = true,
        hasTelephonyManager = false,
      )

    assertThat(provider.cellularNetworkTechnology).isNull()
  }
}
