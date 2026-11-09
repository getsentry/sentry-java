package io.sentry.uitest.android

import android.content.Intent
import android.os.Build
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroidOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnrWarningBreadcrumbTest : BaseUiTest() {
  @AfterTest
  fun resetTimeout() {
    runCommand("device_config delete activity_manager $TIMEOUT_KEY")
  }

  @Test
  fun anrWarningIsRecordedAsBreadcrumb() {
    assumeTrue(
      "ActivityManager.registerAnrWarningListener needs API 37",
      Build.VERSION.SDK_INT >= 37,
    )

    // The grace period for calling startForeground() is device-tunable, so shrink it to keep the
    // test
    // fast. The warning lands at half of whatever this is set to.
    runCommand("device_config put activity_manager $TIMEOUT_KEY $TIMEOUT_MS")

    // beforeBreadcrumb sees the breadcrumb on the thread that records it, so there is no need to
    // capture an event and round-trip it through the relay just to assert on it.
    val recorded = CountDownLatch(1)
    val anrBreadcrumb = AtomicReference<Breadcrumb>()
    initSentry { options: SentryAndroidOptions ->
      options.isEnableAppLifecycleBreadcrumbs = true
      options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
        if (breadcrumb.category == "app.anr") {
          anrBreadcrumb.set(breadcrumb)
          recorded.countDown()
        }
        breadcrumb
      }
    }

    // startForegroundService is only allowed from the foreground.
    launchActivity<EmptyActivity>()

    // SlowStartService never calls startForeground(). It waits past the warning at 50% of the grace
    // period, then stops itself before the 100% deadline, where the system would otherwise throw
    // ForegroundServiceDidNotStartInTimeException. The main thread stays responsive throughout.
    context.startForegroundService(
      Intent(context, SlowStartService::class.java)
        .putExtra(SlowStartService.EXTRA_STOP_SELF_AFTER_MILLIS, STOP_SELF_AFTER_MS)
    )

    val arrived = recorded.await(10, TimeUnit.SECONDS)
    assertTrue(arrived, "No ANR warning within 10s")

    val breadcrumb = anrBreadcrumb.get()
    assertNotNull(breadcrumb)
    assertEquals("system", breadcrumb.type)
    assertEquals("app.anr", breadcrumb.category)
    assertEquals("ANR warning", breadcrumb.message)
    assertEquals("start_foreground_service", breadcrumb.getData("anr_type"))
    assertNotNull(breadcrumb.getData("anr_id"))
    assertNotNull(breadcrumb.getData("consumed_ms"))
  }

  private companion object {
    const val TIMEOUT_KEY = "service_start_foreground_timeout_ms"
    const val TIMEOUT_MS = 4_000
    // Comfortably after the warning at 50% of TIMEOUT_MS, comfortably before the deadline at 100%.
    const val STOP_SELF_AFTER_MS = 2_800L
  }
}
