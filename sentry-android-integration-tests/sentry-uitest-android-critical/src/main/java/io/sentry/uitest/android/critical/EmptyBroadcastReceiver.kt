package io.sentry.uitest.android.critical

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class EmptyBroadcastReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "EmptyBroadcastReceiver"
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    val pendingResult = goAsync()
    Log.d(TAG, "onReceive: broadcast received")
    Thread {
        Thread.sleep(1000)
        pendingResult.finish()
      }
      .start()
  }
}
