package io.sentry.uitest.android.benchmark.util

import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import java.util.concurrent.TimeUnit

// 60 FPS is the recommended target: https://www.youtube.com/watch?v=CaMTIgxCSqU
private const val FRAME_DURATION_60FPS_NS: Double = 1_000_000_000 / 60.0

/**
 * Class that allows to benchmark some operations.
 * Create two [BenchmarkOperation] objects and compare them using [BenchmarkOperation.compare] to get
 * a [BenchmarkResult] with relative or absolute measured overheads.
 */
internal class BenchmarkOperation(
    private val choreographer: Choreographer,
    private val before: (() -> Unit)? = null,
    private val after: (() -> Unit)? = null,
    private val op: () -> Unit
) {

    companion object {

        /**
         * Running two operations sequentially (running 10 times the first and then 10 times the second) results in the
         * first operation to always be slower, so comparing two different operations on equal terms is not possible.
         * This method runs [op1] and [op2] in an alternating sequence.
         * When [op1] and [op2] are the same, we get (nearly) identical results, as expected.
         * You can adjust [warmupIterations] and [measuredIterations]. The lower they are, the faster the benchmark,
         *  but accuracy decreases.
         */
        fun compare(
            op1: BenchmarkOperation,
            op1Name: String,
            op2: BenchmarkOperation,
            op2Name: String,
            warmupIterations: Int = 3,
            measuredIterations: Int = 15
        ): BenchmarkResult {
            // The first operations are the slowest, as the device is still doing things like filling the cache.
            repeat(warmupIterations) {
                op1.warmup()
                op2.warmup()
            }
            // Now we can measure the operations (in alternating sequence).
            repeat(measuredIterations) {
                op1.iterate()
                op2.iterate()
            }
            val op1Result = op1.getResult(op1Name)
            val op2Result = op2.getResult(op2Name)

            // Let's print the raw results.
            println("=====================================")
            println(op1Name)
            println(op1Result)
            println("=====================================")
            println(op2Name)
            println(op2Result)
            println("=====================================")

            return op2Result.compare(op1Result)
        }
    }

    private var iterations: Int = 0
    private var durationNanos: Long = 0
    private var cpuDurationMillis: Long = 0
    private var frames: Int = 0
    private var droppedFrames: Double = 0.0
    private var lastFrameTimeNanos: Long = 0

    /** Run the operation without measuring it. */
    private fun warmup() {
        before?.invoke()
        op()
        after?.invoke()
        isolate()
    }

    /** Run the operation and measure it, updating sentry-uitest-android-benchmark data. */
    private fun iterate() {
        before?.invoke()
        Thread.sleep(200)
        val startRealtimeNs = SystemClock.elapsedRealtimeNanos()
        val startCpuTimeMs = Process.getElapsedCpuTime()

        lastFrameTimeNanos = startRealtimeNs
        iterations++
        choreographer.postFrameCallback(frameCallback)

        op()

        choreographer.removeFrameCallback(frameCallback)
        cpuDurationMillis += Process.getElapsedCpuTime() - startCpuTimeMs
        durationNanos += SystemClock.elapsedRealtimeNanos() - startRealtimeNs

        after?.invoke()
        isolate()
    }

    /** Return the [BenchmarkOperationResult] for the operation. */
    private fun getResult(operationName: String): BenchmarkOperationResult = BenchmarkOperationResult(
        cpuDurationMillis / iterations,
        droppedFrames / iterations,
        durationNanos / iterations,
        // fps = counted frames per seconds converted into frames per nanoseconds, divided by duration in nanoseconds
        // We don't convert the duration into seconds to avoid issues with rounding and possible division by 0
        (frames * TimeUnit.SECONDS.toNanos(1) / durationNanos).toInt(),
        operationName
    )

    /**
     * Helps ensure that operations don't impact one another.
     * Doesn't appear to currently have an impact on the benchmark.
     */
    private fun isolate() {
        Thread.sleep(500)
        Runtime.getRuntime().gc()
        Thread.sleep(100)
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
