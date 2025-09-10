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
    // Test binary identifier extraction works
    val binaryIdentifier = getBinaryIdentifier(context)
    return if (binaryIdentifier != null) {
      UpdateStatus.Error(
        "Binary identifier extracted: $binaryIdentifier. HTTP client and API models coming in future PRs."
      )
    } else {
      UpdateStatus.Error(
        "Could not extract binary identifier. HTTP client and API models coming in future PRs."
      )
    }
  }

  fun checkForUpdateCompletableFuture(context: Context): CompletableFuture<UpdateStatus> {
    val future = CompletableFuture<UpdateStatus>()
    future.complete(checkForUpdate(context))
    return future
  }
}
