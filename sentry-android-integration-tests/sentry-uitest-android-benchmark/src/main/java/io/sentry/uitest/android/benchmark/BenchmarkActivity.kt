package io.sentry.uitest.android.benchmark

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import io.sentry.uitest.android.benchmark.databinding.ActivityBenchmarkBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A simple activity with a list of bitmaps. */
class BenchmarkActivity : AppCompatActivity() {
  companion object {
    /** The activity will set this when scrolling. */
    val scrollingIdlingResource =
      CountingIdlingResource("sentry-uitest-android-benchmark-activityScrolling")

    /** The refresh rate of the device, set on activity create. */
    var refreshRate: Float? = null

    internal const val EXTRA_SUSTAINED_PERFORMANCE_MODE = "EXTRA_SUSTAINED_PERFORMANCE_MODE"
  }

  /**
   * Each background thread will run non-stop calculations during the benchmark. One such thread
   * seems enough to represent a busy application. This number can be increased to mimic busier
   * applications.
   */
  private val backgroundThreadPoolSize = 1
  private val executor: ExecutorService = Executors.newFixedThreadPool(backgroundThreadPoolSize)
  private var resumed = false
  private lateinit var binding: ActivityBenchmarkBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        savedInstanceState?.getBoolean(EXTRA_SUSTAINED_PERFORMANCE_MODE) == true
    ) {
      window.setSustainedPerformanceMode(true)
    }

    refreshRate =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.refreshRate
      } else {
        windowManager.defaultDisplay.refreshRate
      }
    binding = ActivityBenchmarkBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // We show a simple list that changes the idling resource
    binding.benchmarkTransactionList.apply {
      layoutManager = LinearLayoutManager(this@BenchmarkActivity)
      adapter = BenchmarkTransactionListAdapter()
      addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
          override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
              scrollingIdlingResource.increment()
            }
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
              scrollingIdlingResource.decrement()
            }
          }
        }
      )
    }
  }

  @Suppress("MagicNumber")
  override fun onResume() {
    super.onResume()
    resumed = true

    // Do operations until the activity is paused.
    repeat(backgroundThreadPoolSize) {
      executor.execute {
        var x = 0
        for (i in 0..1_000_000_000) {
          x += i * i
          if (!resumed) break
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    resumed = false
  }
}
