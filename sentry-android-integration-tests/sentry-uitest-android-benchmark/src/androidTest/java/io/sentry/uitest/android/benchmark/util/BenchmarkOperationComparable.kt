package io.sentry.uitest.android.benchmark.util

import java.util.concurrent.TimeUnit

/** Stores the results of a single run of [BenchmarkOperation]. */
internal data class BenchmarkOperationComparable(
    val cpuTimeNanos: List<Long>,
    val droppedFrames: List<Double>,
    val durationNanos: List<Long>,
    val fps: List<Int>,
    val operationName: String,
) {
    /** Compare two [BenchmarkOperation], calculating increases of each parameter. */
    fun compare(
        other: BenchmarkOperationComparable,
        iterations: Int,
        refreshRate: Float,
    ): BenchmarkComparisonResult {
        val cores = Runtime.getRuntime().availableProcessors()
        val durationIncreaseNanos = ArrayList<Long>()
        val durationIncreasePercentage = ArrayList<Double>()
        val cpuTimeIncreaseNanos = ArrayList<Long>()
        val cpuTimeOverheadPercentage = ArrayList<Double>()
        val fpsDecrease = ArrayList<Int>()
        val fpsDecreasePercentage = ArrayList<Double>()
        val droppedFramesIncrease = ArrayList<Double>()
        val droppedFramesIncreasePercentage = ArrayList<Double>()

        repeat(iterations) { index ->
            // Measure average duration
            durationIncreaseNanos.add(durationNanos[index] - other.durationNanos[index])
            durationIncreasePercentage.add(durationIncreaseNanos[index] * 100.0 / other.durationNanos[index])

            // Measure average cpu time
            // Cpu time spent profiling is weighted based on available threads, as profiling runs on 1 thread only.
            cpuTimeIncreaseNanos.add((cpuTimeNanos[index] - other.cpuTimeNanos[index]) / cores)
            cpuTimeOverheadPercentage.add(cpuTimeIncreaseNanos[index] * 100.0 / other.cpuTimeNanos[index])

            // Measure average fps
            fpsDecrease.add(other.fps[index] - fps[index])
            fpsDecreasePercentage.add(fpsDecrease[index] * 100.0 / other.fps[index])

            // Measure average dropped frames
            droppedFramesIncrease.add(droppedFrames[index] - other.droppedFrames[index])
            val totalExpectedFrames = TimeUnit.NANOSECONDS.toMillis(other.durationNanos[index]) * refreshRate / 1000
            droppedFramesIncreasePercentage.add(
                droppedFramesIncrease[index] * 100 / (totalExpectedFrames - other.droppedFrames[index]),
            )
        }

        return BenchmarkComparisonResult(
            iterations,
            refreshRate,
            cores,
            operationName,
            other.operationName,
            cpuTimeNanos,
            other.cpuTimeNanos,
            cpuTimeIncreaseNanos,
            cpuTimeOverheadPercentage,
            droppedFrames,
            other.droppedFrames,
            droppedFramesIncrease,
            droppedFramesIncreasePercentage,
            durationNanos,
            other.durationNanos,
            durationIncreaseNanos,
            durationIncreasePercentage,
            fps,
            other.fps,
            fpsDecrease,
            fpsDecreasePercentage,
        )
    }
}
