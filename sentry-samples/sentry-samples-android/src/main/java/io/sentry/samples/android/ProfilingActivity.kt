package io.sentry.samples.android

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
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

        binding = ActivityProfilingBinding.inflate(layoutInflater)

        binding.profilingDurationSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar, p1: Int, p2: Boolean) {
                val seconds = getProfileDuration(p0)
                binding.profilingDurationText.text = getString(R.string.profiling_duration, seconds)
            }
            override fun onStartTrackingTouch(p0: SeekBar) {}
            override fun onStopTrackingTouch(p0: SeekBar) {}
        })
        val initialDurationSeconds = getProfileDuration(binding.profilingDurationSeekbar)
        binding.profilingDurationText.text = getString(R.string.profiling_duration, initialDurationSeconds)

        binding.profilingThreadsSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar, p1: Int, p2: Boolean) {
                val backgroundThreads = getBackgroundThreads(p0)
                binding.profilingThreadsText.text = getString(R.string.profiling_threads, backgroundThreads)
            }
            override fun onStartTrackingTouch(p0: SeekBar) {}
            override fun onStopTrackingTouch(p0: SeekBar) {}
        })
        val initialBackgroundThreads = getBackgroundThreads(binding.profilingThreadsSeekbar)
        binding.profilingThreadsSeekbar.max = Runtime.getRuntime().availableProcessors() - 1
        binding.profilingThreadsText.text = getString(R.string.profiling_threads, initialBackgroundThreads)

        binding.profilingList.adapter = ProfilingListAdapter()
        binding.profilingList.layoutManager = LinearLayoutManager(this)

        binding.profilingStart.setOnClickListener {
            binding.profilingProgressBar.visibility = View.VISIBLE
            profileFinished = false
            val seconds = getProfileDuration(binding.profilingDurationSeekbar)
            val threads = getBackgroundThreads(binding.profilingThreadsSeekbar)
            val t = Sentry.startTransaction("Profiling Test", "$seconds s - $threads threads")
            repeat(threads) {
                executors.submit { runMathOperations() }
            }
            executors.submit { swipeList() }
            binding.profilingStart.postDelayed({ finishTransactionAndPrintResults(t) }, (seconds * 1000).toLong())
        }
        setContentView(binding.root)
    }

    private fun finishTransactionAndPrintResults(t: ITransaction) {
        t.finish()
        binding.profilingProgressBar.visibility = View.GONE
        profileFinished = true
        val profilesDirPath = Sentry.getCurrentHub().options.profilingTracesDirPath
        if (profilesDirPath == null) {
            Toast.makeText(this, R.string.profiling_running, Toast.LENGTH_SHORT).show()
            return
        }
        // Get the last trace file, which is the current profile
        val origProfileFile = File(profilesDirPath).listFiles()?.maxByOrNull { f -> f.lastModified() }
        // Create a new profile file and copy the content of the original file into it
        val profile = File(cacheDir, UUID.randomUUID().toString())
        origProfileFile?.copyTo(profile)
        val profileLength = profile.length()
        val traceData = ProfilingTraceData(profile, t)
        // Create envelope item from copied profile
        val item =
            SentryEnvelopeItem.fromProfilingTrace(traceData, Long.MAX_VALUE, Sentry.getCurrentHub().options.serializer)
        val itemData = item.data

        // Compress the envelope item using Gzip
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).bufferedWriter().use { it.write(String(itemData)) }

        binding.profilingResult.text =
            getString(R.string.profiling_result, profileLength, itemData.size, bos.toByteArray().size)
    }

    private fun swipeList() {
        while (!profileFinished) {
            if ((binding.profilingList.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() == 0) {
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

    private fun fibonacci(n: Int): Int {
        return when {
            profileFinished -> n // If we destroy the activity we stop this function
            n <= 1 -> 1
            else -> fibonacci(n - 1) + fibonacci(n - 2)
        }
    }

    override fun onResume() {
        super.onResume()
        Sentry.getSpan()?.finish()
    }

    override fun onBackPressed() {
        if (profileFinished) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, R.string.profiling_running, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getProfileDuration(s: SeekBar): Float {
        // Minimum duration of the profile is 100 milliseconds
        return s.progress / 10.0F + 0.1F
    }

    private fun getBackgroundThreads(s: SeekBar): Int {
        // Minimum duration of the profile is 100 milliseconds
        return s.progress.coerceIn(0, Runtime.getRuntime().availableProcessors() - 1)
    }
}
