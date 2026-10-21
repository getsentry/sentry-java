package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.test.getProperty
import io.sentry.time.TestMonotonicTicker
import java.net.InetAddress
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HostnameCacheTest {

  private fun getSut(): HostnameCache {
    val address = mock<InetAddress>()
    whenever(address.canonicalHostName).thenReturn("myhost")
    return HostnameCache({ address }, TestMonotonicTicker())
  }

  @Test
  fun `hostname is resolved and cached`() {
    val cache = getSut()
    assertThat(cache.hostname).isEqualTo("myhost")
  }

  @Test
  fun `hostname is re-resolved only once the cache duration has elapsed`() {
    val ticker = TestMonotonicTicker()
    val address = mock<InetAddress>()
    whenever(address.canonicalHostName).thenReturn("first", "second")
    val cache = HostnameCache({ address }, ticker)

    assertThat(cache.hostname).isEqualTo("first")

    ticker.advance(4, TimeUnit.HOURS)
    assertThat(cache.hostname).isEqualTo("first")

    ticker.advance(1, TimeUnit.HOURS)
    assertThat(cache.hostname).isEqualTo("second")
  }

  @Test
  fun `a refresh that cannot be queued does not stop later refreshes`() {
    val ticker = TestMonotonicTicker()
    val address = mock<InetAddress>()
    whenever(address.canonicalHostName).thenReturn("myhost")
    val cache = HostnameCache({ address }, ticker)
    // Reject the next submit the way an executor that could not start a thread would, and let the
    // cache go stale so that reading the hostname attempts a refresh.
    cache.getProperty<ThreadPoolExecutor>("executorService").shutdown()
    ticker.advance(5, TimeUnit.HOURS)

    assertThat(cache.hostname).isEqualTo("myhost")

    // The callable never ran, so nothing else clears this flag; left set, it would fail the
    // compareAndSet guard in getHostname() and no refresh would ever be attempted again.
    assertThat(cache.getProperty<AtomicBoolean>("updateRunning").get()).isFalse()
  }

  @Test
  fun `worker thread times out while idle instead of staying alive`() {
    val cache = getSut()
    val executorService = cache.getProperty<ThreadPoolExecutor>("executorService")
    assertThat(executorService.allowsCoreThreadTimeOut()).isTrue()
    assertThat(executorService.corePoolSize).isEqualTo(1)
    assertThat(executorService.maximumPoolSize).isEqualTo(1)
  }
}
