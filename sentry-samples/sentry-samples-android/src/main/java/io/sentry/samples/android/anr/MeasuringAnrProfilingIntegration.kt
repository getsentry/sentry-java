package io.sentry.samples.android.anr

import android.os.Build
import android.os.Debug
import android.os.Trace
import io.sentry.android.core.anr.AnrProfilingIntegration
import io.sentry.android.core.anr.AnrStackTrace

/**
 * Wraps each main-thread check, stack capture and stack write in an [android.os.Trace] section and
 * reports the CPU time of the polling thread as a trace counter once it stops, so the overhead can
 * be read from a Perfetto trace.
 */
class MeasuringAnrProfilingIntegration(
  pollingIntervalMs: Long,
  samplingIntervalMs: Long,
  suspicionThresholdMs: Long,
) : AnrProfilingIntegration(pollingIntervalMs, samplingIntervalMs, suspicionThresholdMs) {

  override fun run() {
    val cpuStartNs = Debug.threadCpuTimeNanos()
    try {
      super.run()
    } finally {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Trace.setCounter(CPU_COUNTER, Debug.threadCpuTimeNanos() - cpuStartNs)
      }
    }
  }

  override fun checkMainThread(mainThread: Thread) {
    Trace.beginSection(CHECK_SECTION)
    try {
      super.checkMainThread(mainThread)
    } finally {
      Trace.endSection()
    }
  }

  override fun captureStackTrace(mainThread: Thread): AnrStackTrace {
    Trace.beginSection(CAPTURE_SECTION)
    try {
      return super.captureStackTrace(mainThread)
    } finally {
      Trace.endSection()
    }
  }

  override fun addStackTrace(trace: AnrStackTrace) {
    Trace.beginSection(WRITE_SECTION)
    try {
      super.addStackTrace(trace)
    } finally {
      Trace.endSection()
    }
  }

  companion object {
    const val CHECK_SECTION = "AnrProfiling.check"
    const val CAPTURE_SECTION = "AnrProfiling.captureStack"
    const val WRITE_SECTION = "AnrProfiling.writeStack"
    const val CPU_COUNTER = "AnrProfiling.cpuNs"
  }
}
