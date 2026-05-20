package io.sentry.android.ndk

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.ndk.NdkOptions
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
    initialize(false) { assertThat(it.isTombstoneEnabled).isFalse() }
  }

  @Test
  fun `enables supported tombstone enrichment`() {
    initialize(true) { assertThat(it.isTombstoneEnabled).isTrue() }
  }

  @Test
  @Config(sdk = [30])
  fun `disables unavailable tombstone enrichment`() {
    initialize(true) { assertThat(it.isTombstoneEnabled).isFalse() }
  }

  private fun initialize(tombstones: Boolean, verify: (NdkOptions) -> Unit) {
    val context = mock<Context>()
    val options =
      SentryAndroidOptions().apply {
        dsn = "https://key@sentry.io/proj"
        cacheDirPath = "/cache"
        isTombstoneEnabled = tombstones
      }
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use { native ->
      Mockito.mockStatic(SentryNdkCrashService::class.java).use { service ->
        var captured: NdkOptions? = null
        native
          .`when`<Any> { io.sentry.ndk.SentryNdk.init(any<NdkOptions>()) }
          .doAnswer { captured = it.arguments[0] as NdkOptions }
        service.`when`<Boolean> { SentryNdkCrashService.start(context, options) }.thenReturn(true)

        SentryNdk.init(context, options)

        service.verify { SentryNdkCrashService.start(context, options) }
        assertThat(captured).isNotNull()
        verify(captured!!)
      }
    }
  }
}
