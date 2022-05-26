package io.sentry.uitest.android.benchmark

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.sentry.uitest.android.benchmark.databinding.ActivityBenchmarkBinding
import io.sentry.uitest.android.utils.BooleanIdlingResource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A simple activity with a list of bitmaps. */
class BenchmarkActivity : AppCompatActivity() {

    companion object {

        /** The activity will set this when scrolling. */
        val scrollingIdlingResource = BooleanIdlingResource("sentry-uitest-android-benchmark-activity")
    }

    /**
     * Each background thread will run non-stop calculations during the benchmark.
     * One such thread seems enough to represent a busy application.
     * This number can be increased to mimic busier applications.
     */
    private val backgroundThreadPoolSize = 1
    private val executor: ExecutorService = Executors.newFixedThreadPool(backgroundThreadPoolSize)
    private var resumed = false
    private lateinit var binding: ActivityBenchmarkBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // We show a simple list that changes the idling resource
        binding.benchmarkTransactionList.apply {
            layoutManager = LinearLayoutManager(this@BenchmarkActivity)
            adapter = BenchmarkTransactionListAdapter()
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    scrollingIdlingResource.setIdle(newState == RecyclerView.SCROLL_STATE_IDLE)
                }
            })
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
