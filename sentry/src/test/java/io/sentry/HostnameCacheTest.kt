package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.test.getProperty
import java.net.InetAddress
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HostnameCacheTest {

  private fun getSut(): HostnameCache {
    val address = mock<InetAddress>()
    whenever(address.canonicalHostName).thenReturn("myhost")
    return HostnameCache(TimeUnit.HOURS.toMillis(1)) { address }
  }

  @Test
  fun `hostname is resolved and cached`() {
    val cache = getSut()
    assertThat(cache.hostname).isEqualTo("myhost")
  }

  @Test
  fun `worker thread times out while idle instead of staying alive`() {
    val cache = getSut()
    val executorService = cache.getProperty<ThreadPoolExecutor>("executorService")
    assertThat(executorService.allowsCoreThreadTimeOut()).isTrue()
    assertThat(executorService.corePoolSize).isEqualTo(1)
    assertThat(executorService.maximumPoolSize).isEqualTo(1)
  }

  @Test
  fun `close shuts the executor down`() {
    val cache = getSut()
    cache.close()
    assertThat(cache.isClosed).isTrue()
  }
}
