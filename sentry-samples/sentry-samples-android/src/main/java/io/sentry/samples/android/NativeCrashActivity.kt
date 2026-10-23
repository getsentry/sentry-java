package io.sentry.samples.android

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

class NativeCrashActivity : Activity() {
  private val handler = Handler(Looper.getMainLooper())
  private var crash: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val id = intent.getStringExtra("scenario")
    val scenario = NativeCrashScenario.entries.firstOrNull { it.id == id }
    if (scenario == null) {
      Log.e("SentryCrashScenario", "Unknown scenario: $id")
      finish()
      return
    }
    val runId = intent.getStringExtra("run_id") ?: UUID.randomUUID().toString()
    crash = Runnable { scenario.run(runId) }.also { handler.postDelayed(it, 1000) }
  }

  override fun onDestroy() {
    crash?.let { handler.removeCallbacks(it) }
    super.onDestroy()
  }
}
