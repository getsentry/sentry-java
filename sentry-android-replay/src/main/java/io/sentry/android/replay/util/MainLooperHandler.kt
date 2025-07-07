package io.sentry.android.replay.util

import android.os.Handler
import android.os.Looper

internal class MainLooperHandler(looper: Looper = Looper.getMainLooper()) {
  val handler = Handler(looper)

  fun post(runnable: Runnable): Boolean {
    return handler.post(runnable)
  }

  fun postDelayed(runnable: Runnable?, delay: Long): Boolean {
    return handler.postDelayed(runnable ?: return false, delay)
  }

  fun removeCallbacks(runnable: Runnable?) {
    handler.removeCallbacks(runnable ?: return)
  }
}
