package io.sentry.android.distribution.internal

import android.content.Context
import io.sentry.android.distribution.DistributionOptions
import io.sentry.android.distribution.UpdateStatus
import java.util.concurrent.CompletableFuture

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

  fun checkForUpdate(context: Context): UpdateStatus {
    return UpdateStatus.Error("Implementation coming in future PR")
  }

  fun checkForUpdateCompletableFuture(context: Context): CompletableFuture<UpdateStatus> {
    val future = CompletableFuture<UpdateStatus>()
    future.complete(UpdateStatus.Error("Implementation coming in future PR"))
    return future
  }
}
