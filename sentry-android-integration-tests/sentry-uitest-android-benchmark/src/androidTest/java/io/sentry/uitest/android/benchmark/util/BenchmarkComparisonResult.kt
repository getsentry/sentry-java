package io.sentry.uitest.android.benchmark.util

import java.util.concurrent.TimeUnit

/** Result of the [BenchmarkOperation] comparison. */
internal data class BenchmarkComparisonResult(
    /** Number of measured iterations. */
    val iterations: Int,
    /** Screen refresh rate. */
    val refreshRate: Float,
    /** Screen refresh rate. */
    val cores: Int,
    /** Name of the first compared operation. */
    val op1Name: String,
    /** Name of the second compared operation. */
    val op2Name: String,
    /** Raw cpu time in milliseconds of op1. */
    val op1CpuTime: List<Long>,
    /** Raw cpu time in milliseconds of op2. */
    val op2CpuTime: List<Long>,
    /** Increase of cpu time in milliseconds. */
    val cpuTimeIncreases: List<Long>,
    /** Increase of cpu time in percentage. */
    val cpuTimeIncreasePercentages: List<Double>,
    /** Raw dropped frames of op1. */
    val op1DroppedFrames: List<Double>,
    /** Raw dropped frames of op2. */
    val op2DroppedFrames: List<Double>,
    /** Increase of dropped frames. */
    val droppedFramesIncreases: List<Double>,
    /** Increase of dropped frames in percentage. */
    val droppedFramesIncreasePercentages: List<Double>,
    /** Raw duration in nanoseconds of op1. */
    val op1Duration: List<Long>,
    /** Raw duration in nanoseconds of op2. */
    val op2Duration: List<Long>,
    /** Increase of duration in nanoseconds. If it's low enough, no end user will ever realize it. */
    val durationIncreaseNanos: List<Long>,
    /** Increase of duration in percentage. */
    val durationIncreasePercentage: List<Double>,
    /** Raw fps of op1. */
    val op1Fps: List<Int>,
    /** Raw fps of op2. */
    val op2Fps: List<Int>,
    /** Decrease of fps. */
    val fpsDecreases: List<Int>,
    /** Decrease of fps in percentage. */
    val fpsDecreasePercentages: List<Double>,
) {
    /**
     * Prints the raw results of all runs of the comparison.
     * Each printed line is prefixed by [prefix], to allow parsers to easily parse log files to read raw values.
     */
    fun printAllRuns(prefix: String) {
        repeat(iterations) { index ->

            println("$prefix ==================== Iteration $index ====================")

            println(
                "$prefix [$op2Name]: duration=${op2Duration[index]} ns, cpuTime=${op2CpuTime[index]}, fps=${op2Fps[index]}, droppedFrames=${op2DroppedFrames[index]}",
            )
            println(
                "$prefix [$op1Name]: duration=${op1Duration[index]} ns, cpuTime=${op1CpuTime[index]}, fps=${op1Fps[index]}, droppedFrames=${op1DroppedFrames[index]}",
            )
            println(
                "$prefix Duration increase: %.2f%% (%d ns = %d ms)".format(
                    durationIncreasePercentage[index],
                    durationIncreaseNanos[index],
                    TimeUnit.NANOSECONDS.toMillis(durationIncreaseNanos[index]),
                ),
            )

            println(
                "$prefix CPU time overhead, over $cores cores: %.2f%% (%d ms)".format(
                    cpuTimeIncreasePercentages[index],
                    TimeUnit.NANOSECONDS.toMillis(cpuTimeIncreases[index]),
                ),
            )

            println("$prefix FPS decrease: %.2f%% (%d fps)".format(fpsDecreasePercentages[index], fpsDecreases[index]))

            val expectedFrames = TimeUnit.NANOSECONDS.toMillis(op2Duration[index]) * refreshRate / 1000
            println(
                "$prefix Frame drop increase, over $expectedFrames total frames, with $refreshRate hz: %.2f%% (%.2f)".format(
                    droppedFramesIncreasePercentages[index],
                    droppedFramesIncreases[index],
                ),
            )
        }
    }

    fun getSummaryResult() =
        BenchmarkSummaryResult(
            calculatePercentile(cpuTimeIncreases, 90),
            calculatePercentile(cpuTimeIncreasePercentages, 90),
            calculatePercentile(droppedFramesIncreases, 90),
            calculatePercentile(droppedFramesIncreasePercentages, 90),
            calculatePercentile(durationIncreaseNanos, 90),
            calculatePercentile(durationIncreasePercentage, 90),
            calculatePercentile(fpsDecreases, 90),
            calculatePercentile(fpsDecreasePercentages, 90),
        )

    /** Calculate the [percentile] of the [list]. [percentile] should be in the range 0, 100. */
    private fun <T : Number> calculatePercentile(
        list: List<T>,
        percentile: Int,
    ): T {
        if (list.isEmpty()) {
            return 0 as T
        }
        val sortedList = list.sortedBy { it.toDouble() }
        val percentileIndex = (list.size * percentile / 100 - 1).coerceIn(0, list.size)
        return sortedList[percentileIndex]
    }
}

/** Result of the [BenchmarkOperation] comparison. */
internal data class BenchmarkSummaryResult(
    /**
     * Increase of cpu time in nanoseconds.
     * It has no direct impact on performance of the app, but it has on battery usage, as the cpu is 'awaken' longer.
     */
    val cpuTimeIncreaseNanos: Long,
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
    val fpsDecreasePercentage: Double,
) {
    /** Prints the summary results of the comparison. */
    fun printResults() {
        println(
            "Duration increase: %.2f%% (%d ns = %d ms)".format(
                durationIncreasePercentage,
                durationIncreaseNanos,
                TimeUnit.NANOSECONDS.toMillis(durationIncreaseNanos),
            ),
        )
        println("CPU time overhead: %.2f%% (%d ms)".format(cpuTimeIncreasePercentage, cpuTimeIncreaseNanos))
        println("FPS decrease: %.2f%% (%d fps)".format(fpsDecreasePercentage, fpsDecrease))
        println("Frame drop increase: %.2f%% (%.2f)".format(droppedFramesIncreasePercentage, droppedFramesIncrease))
    }
}
