package io.sentry.uitest.android.benchmark.util

import java.util.concurrent.TimeUnit

/** Stores the results of a [BenchmarkOperation]. */
internal data class BenchmarkOperationResult(
    val avgCpuTimeMillis: Long,
    val avgDroppedFrames: Double,
    val avgDurationNanos: Long,
    val avgFramesPerSecond: Int,
    val operationName: String
) {
    /** Compare two [BenchmarkOperation], calculating increases of each parameter. */
    fun compare(other: BenchmarkOperationResult): BenchmarkResult {

        // Measure average duration
        val durationIncreaseNanos = avgDurationNanos - other.avgDurationNanos
        val durationIncreasePercentage = durationIncreaseNanos * 100.0 / other.avgDurationNanos
        println("[${other.operationName}] Average duration: ${other.avgDurationNanos} ns")
        println("[$operationName] Average duration: $avgDurationNanos ns")
        println(
            "Duration increase: %.2f%% (%d ns = %d ms)".format(
                durationIncreasePercentage,
                durationIncreaseNanos,
                TimeUnit.NANOSECONDS.toMillis(durationIncreaseNanos)
            )
        )
        if (durationIncreasePercentage <= 0) {
            println("No measurable duration increase detected.")
        }

        println("--------------------")

        // Measure average cpu time
        val cores = Runtime.getRuntime().availableProcessors()
        val cpuTimeIncreaseMillis = (avgCpuTimeMillis - other.avgCpuTimeMillis) / cores
        val cpuTimeOverheadPercentage = cpuTimeIncreaseMillis * 100.0 / other.avgCpuTimeMillis
        // Cpu time spent profiling is weighted based on available threads, as profiling runs on 1 thread only.
        println("The weighted difference of cpu times is $cpuTimeIncreaseMillis ms (over $cores available cores).")
        println("[${other.operationName}] Cpu time: ${other.avgCpuTimeMillis} ms")
        println("[$operationName] Cpu time: $avgCpuTimeMillis ms")
        println("CPU time overhead: %.2f%% (%d ms)".format(cpuTimeOverheadPercentage, cpuTimeIncreaseMillis))
        if (cpuTimeOverheadPercentage <= 0) {
            println("No measurable CPU time overhead detected.")
        }

        println("--------------------")

        // Measure average fps
        val fpsDecrease = other.avgFramesPerSecond - avgFramesPerSecond
        val fpsDecreasePercentage = fpsDecrease * 100.0 / other.avgFramesPerSecond
        println("[${other.operationName}] Average FPS: ${other.avgFramesPerSecond}")
        println("[$operationName] Average FPS: $avgFramesPerSecond")
        println("FPS decrease: %.2f%% (%d fps)".format(fpsDecreasePercentage, fpsDecrease))
        if (fpsDecreasePercentage <= 0) {
            println("No measurable FPS decrease detected.")
        }

        println("--------------------")

        // Measure average dropped frames
        val droppedFramesIncrease = avgDroppedFrames - other.avgDroppedFrames
        val totalExpectedFrames = TimeUnit.NANOSECONDS.toMillis(other.avgDurationNanos) * 60 / 1000
        val droppedFramesIncreasePercentage = droppedFramesIncrease * 100 / (totalExpectedFrames - other.avgDroppedFrames)
        println("Dropped frames are calculated based on a target of 60 frames per second ($totalExpectedFrames total frames).")
        println("[${other.operationName}] Average dropped frames: ${other.avgDroppedFrames}")
        println("[$operationName] Average dropped frames: $avgDroppedFrames")
        println("Frame drop increase: %.2f%% (%.2f)".format(droppedFramesIncreasePercentage, droppedFramesIncrease))
        if (droppedFramesIncreasePercentage <= 0) {
            println("No measurable frame drop increase detected.")
        }

        return BenchmarkResult(
            cpuTimeIncreaseMillis,
            cpuTimeOverheadPercentage,
            droppedFramesIncrease,
            droppedFramesIncreasePercentage,
            durationIncreaseNanos,
            durationIncreasePercentage,
            fpsDecrease,
            fpsDecreasePercentage
        )
    }
}

/** Result of the [BenchmarkOperation] comparison. */
internal data class BenchmarkResult(
    /**
     * Increase of cpu time in milliseconds.
     * It has no direct impact on performance of the app, but it has on battery usage, as the cpu is 'awaken' longer.
     */
    val cpuTimeIncreaseMillis: Long,
    /** Increase of cpu time in percentage. */
    val cpuTimeIncreasePercentage: Double,
    /**
     * Increase of dropped frames.Very important, as it weights dropped frames based on the time
     * passed between each frame. This is the metric end users can perceive as 'performance' in app usage.
     */
    val droppedFramesIncrease: Double,
    /** Increase of dropped frames in percentage. */
    val droppedFramesIncreasePercentage: Double,
    /** Increase of duration in nanoseconds. If it's low enough, no end user will ever realize it. */
    val durationIncreaseNanos: Long,
    /** Increase of duration in percentage. */
    val durationIncreasePercentage: Double,
    /**
     * Decrease of fps. Not really important, as even if fps are the same, the cpu could be
     * doing more work in the frame window, and it could be hidden by checking average fps only.
     */
    val fpsDecrease: Int,
    /** Decrease of fps in percentage. */
    val fpsDecreasePercentage: Double
)
