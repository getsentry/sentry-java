package io.sentry.android.distribution.internal

import android.content.Context
import io.sentry.android.distribution.DistributionOptions
import io.sentry.android.distribution.UpdateStatus

/** Internal implementation for build distribution functionality. */
internal object DistributionInternal {
  private var isInitialized = false

  @Synchronized
  fun init(context: Context, distributionOptions: DistributionOptions) {
    // TODO: Implementation will be added in future PR
    isInitialized = true
  }

  fun isEnabled(): Boolean {
    return isInitialized
  }

  fun checkForUpdateBlocking(context: Context): UpdateStatus {
    return UpdateStatus.Error("Implementation coming in future PR")
  }

  fun checkForUpdateAsync(context: Context, onResult: (UpdateStatus) -> Unit) {
    throw NotImplementedError()
  }
}
