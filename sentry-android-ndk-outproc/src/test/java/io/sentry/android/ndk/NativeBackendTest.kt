package io.sentry.android.ndk

import android.content.Context
import android.content.ServiceConnection
import com.google.common.truth.Truth.assertThat
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.ndk.NdkOptions
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NativeBackendTest {
  @Test
  fun `starts service with tombstones disabled`() {
    initialize({ isTombstoneEnabled = false }) { assertThat(it.isTombstoneEnabled).isFalse() }
  }

  @Test
  fun `enables supported tombstone enrichment`() {
    initialize({ isTombstoneEnabled = true }) { assertThat(it.isTombstoneEnabled).isTrue() }
  }

  @Test
  @Config(sdk = [30])
  fun `disables unavailable tombstone enrichment`() {
    initialize({ isTombstoneEnabled = true }) { assertThat(it.isTombstoneEnabled).isFalse() }
  }

  @Test
  fun `defaults missing traces sample rate to zero`() {
    initialize({ tracesSampleRate = null }) { assertThat(it.tracesSampleRate).isEqualTo(0.0f) }
  }

  @Test
  fun `propagates traces sample rate`() {
    initialize({ tracesSampleRate = 0.75 }) { assertThat(it.tracesSampleRate).isEqualTo(0.75f) }
  }

  @Test
  fun `does not enable app hang tracking by default`() {
    initialize {
      assertThat(it.isEnableAppHangTracking).isFalse()
      assertThat(it.appHangTimeoutMillis).isEqualTo(5000L)
    }
  }

  @Test
  fun `propagates app hang tracking options`() {
    initialize({
      isEnableNdkAppHangTracking = true
      ndkAppHangTimeoutIntervalMillis = 2000
    }) {
      assertThat(it.isEnableAppHangTracking).isTrue()
      assertThat(it.appHangTimeoutMillis).isEqualTo(2000L)
    }
  }

  @Test
  fun `fails without a context`() {
    assertFailsWith<IllegalStateException> { SentryNdk.init(options()) }
  }

  @Test
  @Config(sdk = [29])
  fun `fails below API 30`() {
    assertFailsWith<IllegalStateException> { SentryNdk.init(mock<Context>(), options()) }
  }

  private fun options() =
    SentryAndroidOptions().apply {
      dsn = "https://key@sentry.io/proj"
      cacheDirPath = "/cache"
    }

  private fun initialize(
    configure: SentryAndroidOptions.() -> Unit = {},
    verify: (NdkOptions) -> Unit,
  ) {
    val context = mock<Context>()
    val options = options().apply(configure)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use { native ->
      Mockito.mockStatic(SentryNdkCrashService::class.java).use { service ->
        var captured: NdkOptions? = null
        native
          .`when`<Any> { io.sentry.ndk.SentryNdk.init(any<NdkOptions>()) }
          .doAnswer { captured = it.arguments[0] as NdkOptions }
        service
          .`when`<ServiceConnection> { SentryNdkCrashService.start(context, options) }
          .thenReturn(mock<ServiceConnection>())

        SentryNdk.init(context, options)

        service.verify { SentryNdkCrashService.start(context, options) }
        assertThat(captured).isNotNull()
        verify(captured!!)
      }
    }
  }
}
