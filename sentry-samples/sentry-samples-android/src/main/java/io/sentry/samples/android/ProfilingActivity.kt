package io.sentry.samples.android

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.sentry.Sentry
import io.sentry.samples.android.databinding.ActivityProfilingBinding
import java.util.concurrent.Executors

class ProfilingActivity : AppCompatActivity() {
  private lateinit var binding: ActivityProfilingBinding
  private val executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
  private var profileFinished = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (profileFinished) {
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
          } else {
            Toast.makeText(this@ProfilingActivity, R.string.profiling_running, Toast.LENGTH_SHORT)
              .show()
          }
        }
      },
    )
    binding = ActivityProfilingBinding.inflate(layoutInflater)

    binding.profilingDurationSeekbar.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(p0: SeekBar, p1: Int, p2: Boolean) {
          binding.profilingDurationText.text =
            getString(R.string.profiling_duration, getProfileDuration())
        }

        override fun onStartTrackingTouch(p0: SeekBar) {}

        override fun onStopTrackingTouch(p0: SeekBar) {}
      }
    )
    binding.profilingDurationText.text =
      getString(R.string.profiling_duration, getProfileDuration())

    binding.profilingThreadsSeekbar.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(p0: SeekBar, p1: Int, p2: Boolean) {
          binding.profilingThreadsText.text =
            getString(R.string.profiling_threads, getBackgroundThreads())
        }

        override fun onStartTrackingTouch(p0: SeekBar) {}

        override fun onStopTrackingTouch(p0: SeekBar) {}
      }
    )
    binding.profilingThreadsSeekbar.max = Runtime.getRuntime().availableProcessors() - 1
    binding.profilingThreadsText.text =
      getString(R.string.profiling_threads, getBackgroundThreads())

    binding.profilingList.adapter = ProfilingListAdapter()
    binding.profilingList.layoutManager = LinearLayoutManager(this)

    binding.profilingStart.setOnClickListener {
      binding.profilingProgressBar.visibility = View.VISIBLE
      profileFinished = false
      val seconds = getProfileDuration()
      val threads = getBackgroundThreads()

      Sentry.startProfiler()
      repeat(threads) { executors.submit { runMathOperations() } }
      executors.submit { swipeList() }

      Thread {
          Thread.sleep((seconds * 1000).toLong())
          profileFinished = true
          Sentry.stopProfiler()

          binding.root.post {
            binding.profilingProgressBar.visibility = View.GONE
            binding.profilingResult.text =
              getString(R.string.profiling_result_done, seconds, threads)
          }
        }
        .start()
    }
    setContentView(binding.root)
    Sentry.reportFullyDisplayed()
  }

  private fun swipeList() {
    while (!profileFinished) {
      if (
        (binding.profilingList.layoutManager as? LinearLayoutManager)
          ?.findFirstVisibleItemPosition() == 0
      ) {
        binding.profilingList.smoothScrollToPosition(100)
      } else {
        binding.profilingList.smoothScrollToPosition(0)
      }
      Thread.sleep(3000)
    }
  }

  private fun runMathOperations() {
    while (!profileFinished) {
      fibonacci(25)
    }
  }

  private fun fibonacci(n: Int): Int =
    when {
      profileFinished -> n
      n <= 1 -> 1
      else -> fibonacci(n - 1) + fibonacci(n - 2)
    }

  private fun getProfileDuration(): Float {
    return binding.profilingDurationSeekbar.progress / 10.0F + 0.1F
  }

  private fun getBackgroundThreads(): Int {
    return binding.profilingThreadsSeekbar.progress.coerceIn(
      0,
      Runtime.getRuntime().availableProcessors() - 1,
    )
  }
}
