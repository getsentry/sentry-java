package io.sentry.android.ndk

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.ndk.NdkOptions
import java.util.Date
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], shadows = [SentryShadowParcelFileDescriptor::class])
class SentryNdkCrashServiceTest {
  private val app = RuntimeEnvironment.getApplication()
  private val component = ComponentName(app, SentryNdkCrashService::class.java)
  private val options =
    SentryAndroidOptions().apply {
      dsn = "https://key@sentry.io/proj"
      cacheDirPath = app.cacheDir.absolutePath
    }

  @Test
  fun `binds without starting the service`() {
    configure(true)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use {
      val connection = SentryNdkCrashService.start(app, options)

      assertThat(connection).isNotNull()
      assertThat(shadowOf(app).boundServiceConnections).containsExactly(connection)
      assertThat(shadowOf(app).unboundServiceConnections).isEmpty()
      // robolectric records the bind intent as started, a startService call would add another
      assertThat(shadowOf(app).allStartedServices).hasSize(1)
    }
  }

  @Test
  fun `unbinds when the service does not configure`() {
    configure(false)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use {
      assertThat(SentryNdkCrashService.start(app, options)).isNull()

      assertThat(shadowOf(app).boundServiceConnections).isEmpty()
      assertThat(shadowOf(app).unboundServiceConnections).hasSize(1)
    }
  }

  @Test
  fun `close unbinds the service`() {
    configure(true)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use {
      SentryNdk.init(app, options)
      val connection = shadowOf(app).boundServiceConnections.single()

      SentryNdk.close()

      assertThat(shadowOf(app).boundServiceConnections).isEmpty()
      assertThat(shadowOf(app).unboundServiceConnections).containsExactly(connection)
    }
  }

  @Test
  fun `init unbinds when native init fails`() {
    configure(true)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use { native ->
      native
        .`when`<Any> { io.sentry.ndk.SentryNdk.init(any<NdkOptions>()) }
        .thenThrow(IllegalStateException())

      assertFailsWith<IllegalStateException> { SentryNdk.init(app, options) }

      assertThat(shadowOf(app).boundServiceConnections).isEmpty()
      assertThat(shadowOf(app).unboundServiceConnections).hasSize(1)
    }
  }

  @Test
  fun `init fails when the service does not bind`() {
    shadowOf(app).declareComponentUnbindable(component)
    Mockito.mockStatic(io.sentry.ndk.SentryNdk::class.java).use { native ->
      assertFailsWith<IllegalStateException> { SentryNdk.init(app, options) }

      native.verify({ io.sentry.ndk.SentryNdk.init(any<NdkOptions>()) }, never())
    }
  }

  @Test
  fun `matches the exit info of the app pid`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    addExitInfo(service, pid = 1, timestamp = 2000)
    addExitInfo(service, pid = 2, timestamp = 1000)

    assertThat(service.findMatchingExitInfo(options, Date(2000), 2)?.pid).isEqualTo(2)
  }

  @Test
  fun `ignores exit infos of other pids`() {
    val service = Robolectric.setupService(SentryNdkCrashService::class.java)
    addExitInfo(service, pid = 1, timestamp = 1000)

    assertThat(service.findMatchingExitInfo(options, Date(1000), 2)).isNull()
  }

  private fun addExitInfo(context: Context, pid: Int, timestamp: Long) {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    shadowOf(activityManager)
      .addApplicationExitInfo(
        ApplicationExitInfoBuilder.newBuilder()
          .setPid(pid)
          .setReason(ApplicationExitInfo.REASON_CRASH_NATIVE)
          .setTimestamp(timestamp)
          .build()
      )
  }

  private fun configure(configured: Boolean) {
    val binder =
      object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
          reply?.writeNoException()
          reply?.writeInt(if (configured) 1 else 0)
          return true
        }
      }
    shadowOf(app).setComponentNameAndServiceForBindService(component, binder)
  }
}
