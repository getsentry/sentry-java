package io.sentry.samples.android.anr

import android.os.Handler
import android.os.SystemClock
import android.os.Trace
import java.util.concurrent.CountDownLatch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Enqueues a burst of messages on the main thread that run back to back, like a backlog in a real
 * app: small callbacks, a few janky frames, one 3 s hiccup and a long task.
 *
 * All messages are posted at once, so the main thread stays busy for about 3.9 s in total. That is
 * above the suspicion threshold of [io.sentry.android.core.anr.AnrProfilingIntegration] (so it
 * starts sampling), but below its 4 s ANR threshold.
 */
internal object MainThreadWorkload {

  private val lock = Object()

  fun post(handler: Handler) {
    repeat(CALLBACK_COUNT) { i -> handler.post { callback(i) } }
    JANK_DURATIONS_MS.forEach { durationMs ->
      handler.post { traced("Workload.jank") { computeLayout(durationMs) } }
    }
    handler.post { traced("Workload.hiccup") { hiccup() } }
    handler.post { traced("Workload.long") { computeLayout(LONG_TASK_MS) } }
    repeat(CALLBACK_COUNT) { i -> handler.post { callback(i) } }
  }

  private fun callback(i: Int) {
    traced("Workload.callback") { updateListItems(2L + i % 4) }
  }

  /** ~2 s of CPU work followed by ~1 s waiting for a lock held by a background thread. */
  private fun hiccup() {
    val start = SystemClock.uptimeMillis()
    parseLargeJson(start + 1_200)
    sortRecords(start + 2_000)
    waitForLockHeldInBackground(HICCUP_LOCK_WAIT_MS)
  }

  private fun parseLargeJson(deadline: Long) {
    val json =
      JSONArray()
        .apply {
          repeat(500) { i ->
            put(JSONObject().put("id", i).put("name", "item-$i").put("score", i * 31 % 97))
          }
        }
        .toString()
    while (SystemClock.uptimeMillis() < deadline) {
      JSONArray(json)
    }
  }

  private fun sortRecords(deadline: Long) {
    var seed = 1L
    while (SystemClock.uptimeMillis() < deadline) {
      val records =
        MutableList(5_000) {
          seed = seed * 6364136223846793005L + 1
          seed
        }
      records.sort()
    }
  }

  private fun waitForLockHeldInBackground(holdMs: Long) {
    val acquired = CountDownLatch(1)
    Thread(
        {
          synchronized(lock) {
            acquired.countDown()
            Thread.sleep(holdMs)
          }
        },
        "WorkloadLockHolder",
      )
      .start()
    acquired.await()
    synchronized(lock) {}
  }

  private fun computeLayout(durationMs: Long) = spin(durationMs)

  private fun updateListItems(durationMs: Long) = spin(durationMs)

  private fun spin(durationMs: Long) {
    val deadline = SystemClock.uptimeMillis() + durationMs
    var x = 0.0
    while (SystemClock.uptimeMillis() < deadline) {
      x += Math.sqrt(x + 1)
    }
  }

  private inline fun traced(name: String, block: () -> Unit) {
    Trace.beginSection(name)
    try {
      block()
    } finally {
      Trace.endSection()
    }
  }

  private const val CALLBACK_COUNT = 20
  private val JANK_DURATIONS_MS = listOf(80L, 120L, 150L)
  private const val HICCUP_LOCK_WAIT_MS = 1_000L
  private const val LONG_TASK_MS = 400L
}
