package io.sentry.uitest.android

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Starts as a foreground service but never calls [startForeground], so the system emits an ANR
 * warning at half of the `service_start_foreground_timeout_ms` grace period. It then stops itself
 * before the grace period expires, because at 100% the system throws
 * ForegroundServiceDidNotStartInTimeException and kills the app.
 *
 * Used by `AnrWarningBreadcrumbTest` to provoke an ANR warning without stalling the main thread.
 */
class SlowStartService : Service() {
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val stopSelfAfterMillis = intent?.getLongExtra(EXTRA_STOP_SELF_AFTER_MILLIS, 0L) ?: 0L
    Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, stopSelfAfterMillis)
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val EXTRA_STOP_SELF_AFTER_MILLIS = "stop_self_after_millis"
  }
}
