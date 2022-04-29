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
    /**
     * Compare two [BenchmarkOperationResult], calculating increases of each parameter in percentage.
     */
    fun compare(other: BenchmarkOperationResult): BenchmarkResult {

        // Measure average duration
        val durationDiffNanos = avgDurationNanos - other.avgDurationNanos
        val durationIncreasePercentage = durationDiffNanos * 100.0 / other.avgDurationNanos
        println(
            "Measuring the duration increase of the operation in the transaction. If it's low enough, " +
                "no end user will ever realize it"
        )
        println("[${other.operationName}] Average duration: ${other.avgDurationNanos} ns")
        println("[$operationName] Average duration: $avgDurationNanos ns")
        if (durationIncreasePercentage > 0) {
            println("Duration increase: %.2f%%".format(durationIncreasePercentage))
        } else {
            println("No measurable duration increase detected.")
        }

        println("--------------------")

        // Measure average cpu time
        val cpuTimeDiff = (avgCpuTimeMillis - other.avgCpuTimeMillis) / Runtime.getRuntime().availableProcessors()
        val cpuTimeOverheadPercentage = cpuTimeDiff * 100.0 / other.avgCpuTimeMillis
        // Cpu time spent profiling is weighted based on available threads, as profiling runs on 1 thread only.
        println(
            "Measuring the increased cpu time. It has no direct impact on performance of the app, " +
                "but it has on battery usage, as the cpu is 'awaken' longer."
        )
        println("The weighted difference of cpu times is $cpuTimeDiff ms.")
        println("[${other.operationName}] Cpu time: ${other.avgCpuTimeMillis} ms")
        println("[$operationName] Cpu time: $avgCpuTimeMillis ms")
        if (cpuTimeOverheadPercentage > 0) {
            println("CPU time overhead: %.2f%%".format(cpuTimeOverheadPercentage))
        } else {
            println("No measurable CPU time overhead detected.")
        }

        println("--------------------")

        // Measure average fps
        val fpsDiff = other.avgFramesPerSecond - avgFramesPerSecond
        val fpsDecreasePercentage = fpsDiff * 100.0 / other.avgFramesPerSecond
        println(
            "Measuring the decreased fps. Not really important, as even if fps are the same, the cpu could be " +
                "doing more work in the frame window, and it could be hidden by checking average fps only."
        )
        println("[${other.operationName}] Average FPS: ${other.avgFramesPerSecond}")
        println("[$operationName] Average FPS: $avgFramesPerSecond")
        if (fpsDecreasePercentage > 0) {
            println("FPS decrease: %.2f%%".format(fpsDecreasePercentage))
        } else {
            println("No measurable FPS decrease detected.")
        }

        println("--------------------")

        // Measure average dropped frames
        val droppedFramesDiff = avgDroppedFrames - other.avgDroppedFrames
        val totalExpectedFrames = TimeUnit.NANOSECONDS.toMillis(other.avgDurationNanos) * 60 / 1000
        val droppedFramesIncreasePercentage = droppedFramesDiff * 100 / (totalExpectedFrames - other.avgDroppedFrames)
        println(
            "Measuring the increased fps drop rate. Very important, as it weights dropped frames based on the " +
                "time passed between each frame. This is the metric end users can perceive as 'performance' in app usage."
        )
        println("Dropped frames are calculated based on a target of 60 frames per second ($totalExpectedFrames total frames).")
        println("[${other.operationName}] Average dropped frames: ${other.avgDroppedFrames}")
        println("[$operationName] Average dropped frames: $avgDroppedFrames")
        if (droppedFramesIncreasePercentage > 0) {
            println("Frame drop increase: %.2f%%".format(droppedFramesIncreasePercentage))
        } else {
            println("No measurable frame drop increase detected.")
        }

        return BenchmarkResult(
            cpuTimeOverheadPercentage,
            droppedFramesIncreasePercentage,
            durationIncreasePercentage,
            fpsDecreasePercentage
        )
    }
}

internal data class BenchmarkResult(
    val cpuTimeIncrease: Double,
    val droppedFramesIncrease: Double,
    val durationIncrease: Double,
    val fpsDecrease: Double
)
