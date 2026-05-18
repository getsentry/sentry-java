package io.sentry.android.core

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(android.os.Process::class)
class SentryShadowProcess {
  companion object {
    private var startUptimeMillis: Long = 0
    private var startElapsedRealtime: Long = 0

    fun setStartUptimeMillis(value: Long) {
      startUptimeMillis = value
    }

    fun setStartElapsedRealtime(value: Long) {
      startElapsedRealtime = value
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun getStartUptimeMillis(): Long = startUptimeMillis

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun getStartElapsedRealtime(): Long = startElapsedRealtime
  }
}
