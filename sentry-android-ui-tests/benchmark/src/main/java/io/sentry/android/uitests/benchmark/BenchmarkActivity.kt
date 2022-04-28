package io.sentry.android.uitests.benchmark

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.IdlingRegistry
import io.sentry.android.uitests.end2end.utils.BooleanIdlingResource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/** A simple activity with a list of bitmaps. */
class BenchmarkActivity : AppCompatActivity() {

    companion object {

        /** The activity will set this manage when scrolling. */
        val scrollingIdlingResource = BooleanIdlingResource("benchmark-activity")

        init {
            IdlingRegistry.getInstance().register(scrollingIdlingResource)
        }
    }

    /**
     * Each background thread will run non-stop calculations during the benchmark.
     * One such thread seems enough to represent a busy application.
     * This number can be increased to mimic busier applications.
     */
    private val backgroundThreadPoolSize = 1
    private val executor: ExecutorService = Executors.newFixedThreadPool(backgroundThreadPoolSize)
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_benchmark)

        findViewById<RecyclerView>(R.id.benchmark_transaction_list).apply {
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
