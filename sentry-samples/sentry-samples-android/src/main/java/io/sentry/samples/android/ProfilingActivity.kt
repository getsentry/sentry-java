package io.sentry.samples.android

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.sentry.ITransaction
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryEnvelopeItem
import io.sentry.samples.android.databinding.ActivityProfilingBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

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
      val t = Sentry.startTransaction("Profiling Test", "$seconds s - $threads threads")
      repeat(threads) { executors.submit { runMathOperations() } }
      executors.submit { swipeList() }

      Thread {
          Thread.sleep((seconds * 1000).toLong())
          finishTransactionAndPrintResults(t)
          binding.root.post { binding.profilingProgressBar.visibility = View.GONE }
        }
        .start()
    }
    setContentView(binding.root)
    Sentry.reportFullyDisplayed()
  }

  private fun finishTransactionAndPrintResults(t: ITransaction) {
    t.finish()
    profileFinished = true
    val profilesDirPath = Sentry.getCurrentScopes().options.profilingTracesDirPath
    if (profilesDirPath == null) {
      Toast.makeText(this, R.string.profiling_no_dir_set, Toast.LENGTH_SHORT).show()
      return
    }

    // We have concurrent profiling now. We have to wait for all transactions to finish (e.g. button
    // click)
    //  before reading the profile, otherwise it's empty and a crash occurs
    if (Sentry.getSpan() != null) {
      val timeout = Sentry.getCurrentScopes().options.idleTimeout ?: 0
      val duration = (getProfileDuration() * 1000).toLong()
      Thread.sleep((timeout - duration).coerceAtLeast(0))
    }

    try {
      // Get the last trace file, which is the current profile
      val origProfileFile = File(profilesDirPath).listFiles()?.maxByOrNull { f -> f.lastModified() }
      // Create a new profile file and copy the content of the original file into it
      val profile = File(cacheDir, UUID.randomUUID().toString())
      origProfileFile?.copyTo(profile)

      val profileLength = profile.length()
      val traceData = ProfilingTraceData(profile, t)
      // Create envelope item from copied profile
      val item =
        SentryEnvelopeItem.fromProfilingTrace(
          traceData,
          Long.MAX_VALUE,
          Sentry.getCurrentScopes().options.serializer,
        )
      val itemData = item.data

      // Compress the envelope item using Gzip
      val bos = ByteArrayOutputStream()
      GZIPOutputStream(bos).bufferedWriter().use { it.write(String(itemData)) }

      binding.root.post {
        binding.profilingResult.text =
          getString(R.string.profiling_result, profileLength, itemData.size, bos.toByteArray().size)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
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
      profileFinished -> n // If we destroy the activity we stop this function
      n <= 1 -> 1
      else -> fibonacci(n - 1) + fibonacci(n - 2)
    }

  private fun getProfileDuration(): Float {
    // Minimum duration of the profile is 100 milliseconds
    return binding.profilingDurationSeekbar.progress / 10.0F + 0.1F
  }

  private fun getBackgroundThreads(): Int {
    // Minimum duration of the profile is 100 milliseconds
    return binding.profilingThreadsSeekbar.progress.coerceIn(
      0,
      Runtime.getRuntime().availableProcessors() - 1,
    )
  }
}
