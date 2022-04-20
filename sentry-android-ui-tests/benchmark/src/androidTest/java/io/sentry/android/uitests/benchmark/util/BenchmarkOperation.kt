package io.sentry.android.uitests.benchmark.util

import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import androidx.test.runner.AndroidJUnitRunner

// 60 FPS is the recommended target: https://www.youtube.com/watch?v=CaMTIgxCSqU
private const val FRAME_DURATION_60FPS_NS: Double = 1_000_000_000 / 60.0

internal class BenchmarkOperation(runner: AndroidJUnitRunner, private val op: () -> Unit) {

    private lateinit var choreographer: Choreographer
    private var iterations: Int = 0
    private var durationNanos: Long = 0
    private var cpuDurationMillis: Long = 0
    private var frames: Int = 0
    private var droppedFrames: Double = 0.0
    private var lastFrameTimeNanos: Long = 0

    init {
        // Must run on the main thread to get the main thread choreographer.
        runner.runOnMainSync {
            choreographer = Choreographer.getInstance()
        }
    }

    fun warmup() {
        op()
        isolate()
    }

    fun iterate() {
        val startRealtimeNs = SystemClock.elapsedRealtimeNanos()
        val startCpuTimeMs = Process.getElapsedCpuTime()

        lastFrameTimeNanos = startRealtimeNs
        iterations++
        choreographer.postFrameCallback(frameCallback)

        op()

        choreographer.removeFrameCallback(frameCallback)
        cpuDurationMillis += Process.getElapsedCpuTime() - startCpuTimeMs
        durationNanos += SystemClock.elapsedRealtimeNanos() - startRealtimeNs

        isolate()
    }

    fun getResult(operationName: String): BenchmarkOperationResult = BenchmarkOperationResult(
        cpuDurationMillis / iterations,
        droppedFrames / iterations,
        durationNanos / iterations,
        (frames * 1_000_000_000L / durationNanos).toInt(),
        operationName
    )

    /**
     * Helps ensure that operations don't impact one another. Doesn't appear to currently have an
     * impact on the benchmark, but it might later on.
     */
    private fun isolate() {
        Thread.sleep(500)
        Runtime.getRuntime().gc()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frames++
            val timeSinceLastFrameNanos = frameTimeNanos - lastFrameTimeNanos
            if (timeSinceLastFrameNanos > FRAME_DURATION_60FPS_NS) {
                // Fractions of frames dropped are weighted to improve the accuracy of the results.
                // For example, 31ms between frames is much worse than 17ms, even though both
                // durations are within the "1 frame dropped" range.
                droppedFrames += timeSinceLastFrameNanos / FRAME_DURATION_60FPS_NS - 1
            }
            lastFrameTimeNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }
}
