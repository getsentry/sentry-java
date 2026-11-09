package io.sentry.android.core.internal.util

import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
import io.sentry.IConnectionStatusProvider.IConnectionStatusObserver
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.S])
class AndroidConnectionStatusProviderConcurrencyTest {
  @Test
  fun `addConnectionStatusObserver() - does not wait for network callback registration`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      PausedCall().use { networkCall ->
        doAnswer {
            networkCall.pause()
            null
          }
          .whenever(fixture.connectivityManager)
          .registerDefaultNetworkCallback(any<NetworkCallback>())
        val registration = fixture.runNextTask()
        networkCall.awaitEntry()
        val observer = mock<IConnectionStatusObserver>()

        val added = onCallerThread { fixture.provider.addConnectionStatusObserver(observer) }

        assertThat(added.get(2, SECONDS)).isFalse()
        networkCall.close()
        registration.get(5, SECONDS)
      }
    }
  }

  @Test
  fun `addConnectionStatusObserver() - does not wait for telephony callback registration`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      PausedCall().use { telephonyCall ->
        doAnswer {
            telephonyCall.pause()
            null
          }
          .whenever(fixture.telephonyManager)
          .registerTelephonyCallback(any(), any())
        val registration = fixture.runNextTask()
        telephonyCall.awaitEntry()
        val observer = mock<IConnectionStatusObserver>()

        val added = onCallerThread { fixture.provider.addConnectionStatusObserver(observer) }

        assertThat(added.get(2, SECONDS)).isTrue()
        telephonyCall.close()
        registration.get(5, SECONDS)
      }
    }
  }

  @Test
  fun `addConnectionStatusObserver() - does not wait for telephony callback unregistration`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      fixture.runNextTask().get(5, SECONDS)
      PausedCall().use { telephonyCall ->
        doAnswer {
            telephonyCall.pause()
            null
          }
          .whenever(fixture.telephonyManager)
          .unregisterTelephonyCallback(any())
        fixture.provider.onBackground()
        val unregistration = fixture.runNextTask()
        telephonyCall.awaitEntry()
        val observer = mock<IConnectionStatusObserver>()

        val added = onCallerThread { fixture.provider.addConnectionStatusObserver(observer) }

        assertThat(added.get(2, SECONDS)).isFalse()
        telephonyCall.close()
        unregistration.get(5, SECONDS)
      }
    }
  }

  @Test
  fun `removeConnectionStatusObserver() - does not wait for telephony callback unregistration`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      fixture.runNextTask().get(5, SECONDS)
      val observer = mock<IConnectionStatusObserver>()
      fixture.provider.addConnectionStatusObserver(observer)
      fixture.runNextTask().get(5, SECONDS)
      PausedCall().use { telephonyCall ->
        doAnswer {
            telephonyCall.pause()
            null
          }
          .whenever(fixture.telephonyManager)
          .unregisterTelephonyCallback(any())
        fixture.provider.onBackground()
        val unregistration = fixture.runNextTask()
        telephonyCall.awaitEntry()

        val removed = onCallerThread { fixture.provider.removeConnectionStatusObserver(observer) }

        removed.get(2, SECONDS)
        telephonyCall.close()
        unregistration.get(5, SECONDS)
      }
    }
  }

  @Test
  fun `observer changes during notification take effect on the next connection change`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      fixture.runNextTask().get(5, SECONDS)
      val first = mock<IConnectionStatusObserver>()
      val second = mock<IConnectionStatusObserver>()
      val replacement = mock<IConnectionStatusObserver>()
      val network = mock<Network>()
      val callback = checkNotNull(fixture.provider.networkCallback)
      doAnswer {
          fixture.provider.removeConnectionStatusObserver(first)
          fixture.provider.removeConnectionStatusObserver(second)
          fixture.provider.addConnectionStatusObserver(replacement)
          null
        }
        .whenever(first)
        .onConnectionStatusChanged(DISCONNECTED)
      fixture.provider.addConnectionStatusObserver(first)
      fixture.provider.addConnectionStatusObserver(second)
      callback.onAvailable(network)

      callback.onLost(network)

      verify(first).onConnectionStatusChanged(DISCONNECTED)
      verify(second).onConnectionStatusChanged(DISCONNECTED)
      verify(replacement, never()).onConnectionStatusChanged(any())

      callback.onAvailable(network)
      callback.onLost(network)

      verify(first).onConnectionStatusChanged(DISCONNECTED)
      verify(second).onConnectionStatusChanged(DISCONNECTED)
      verify(replacement).onConnectionStatusChanged(DISCONNECTED)
    }
  }

  @Test
  fun `pending observer registration shares one network and telephony callback`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      val first = mock<IConnectionStatusObserver>()
      val second = mock<IConnectionStatusObserver>()

      assertThat(fixture.provider.addConnectionStatusObserver(first)).isFalse()
      assertThat(fixture.provider.addConnectionStatusObserver(second)).isFalse()
      repeat(3) { fixture.runNextTask().get(5, SECONDS) }

      verify(fixture.connectivityManager).registerDefaultNetworkCallback(any<NetworkCallback>())
      verify(fixture.telephonyManager).registerTelephonyCallback(any(), any())
    }
  }

  @Test
  fun `observer registration retries after network callback registration fails`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      doAnswer { throw SecurityException("Network registration denied") }
        .doAnswer { null }
        .whenever(fixture.connectivityManager)
        .registerDefaultNetworkCallback(any<NetworkCallback>())
      fixture.runNextTask().get(5, SECONDS)
      verify(fixture.telephonyManager, never()).registerTelephonyCallback(any(), any())

      val added = fixture.provider.addConnectionStatusObserver(mock<IConnectionStatusObserver>())
      fixture.runNextTask().get(5, SECONDS)

      assertThat(added).isFalse()
      verify(fixture.connectivityManager, times(2))
        .registerDefaultNetworkCallback(any<NetworkCallback>())
      verify(fixture.telephonyManager).registerTelephonyCallback(any(), any())
    }
  }

  @Test
  fun `closing during telephony registration unregisters both callbacks after registration completes`() {
    ConnectionStatusProviderFixture.create().use { fixture ->
      PausedCall().use { telephonyCall ->
        doAnswer {
            telephonyCall.pause()
            null
          }
          .whenever(fixture.telephonyManager)
          .registerTelephonyCallback(any(), any())
        val registration = fixture.runNextTask()
        telephonyCall.awaitEntry()

        fixture.provider.close()
        val closing = fixture.runNextTask()

        verify(fixture.telephonyManager, never()).unregisterTelephonyCallback(any())
        telephonyCall.close()
        registration.get(5, SECONDS)
        closing.get(5, SECONDS)
        verify(fixture.connectivityManager).unregisterNetworkCallback(any<NetworkCallback>())
        verify(fixture.telephonyManager).unregisterTelephonyCallback(any())
      }
    }
  }

  private fun <T> onCallerThread(call: () -> T): FutureTask<T> {
    val result = FutureTask(call)
    thread(name = "connection-status-observer-caller") { result.run() }
    return result
  }
}
