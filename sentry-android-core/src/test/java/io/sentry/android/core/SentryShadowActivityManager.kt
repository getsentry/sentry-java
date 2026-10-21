package io.sentry.android.core

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationStartInfo
import android.os.Build
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@Implements(ActivityManager::class, minSdk = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class SentryShadowActivityManager {
  companion object {
    private var historicalProcessStartReasons: List<ApplicationStartInfo> = emptyList()
    private var importance: Int = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    private var historicalProcessStartReasonsException: RuntimeException? = null

    fun setHistoricalProcessStartReasons(startReasons: List<ApplicationStartInfo>) {
      historicalProcessStartReasons = startReasons
    }

    fun setHistoricalProcessStartReasonsException(exception: RuntimeException) {
      historicalProcessStartReasonsException = exception
    }

    fun setImportance(importance: Int) {
      this.importance = importance
    }

    fun reset() {
      historicalProcessStartReasons = emptyList()
      importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
      historicalProcessStartReasonsException = null
    }

    @Implementation
    @JvmStatic
    fun getMyMemoryState(outState: RunningAppProcessInfo) {
      outState.importance = importance
    }
  }

  @Implementation
  fun getHistoricalProcessStartReasons(maxNum: Int): List<ApplicationStartInfo> {
    historicalProcessStartReasonsException?.let { throw it }
    return historicalProcessStartReasons.take(maxNum)
  }
}
