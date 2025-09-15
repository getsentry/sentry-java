package io.sentry.android.distribution.internal

import android.content.Context
import io.sentry.Sentry
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
    val buildIdentifier = Sentry.getCurrentScopes().options.proguardUuid
    return if (!buildIdentifier.isNullOrEmpty()) {
      UpdateStatus.Error(
        "Build identifier from ProGuard UUID: $buildIdentifier. HTTP client and API models coming in future PRs."
      )
    } else {
      UpdateStatus.Error(
        "No ProGuard UUID found. Ensure sentry-debug-meta.properties contains io.sentry.ProguardUuids or set via manifest."
      )
    }
  }

  fun checkForUpdateAsync(context: Context, onResult: (UpdateStatus) -> Unit) {
    // For now, just call the blocking version and return the result
    // In future PRs, this will be truly async
    val result = checkForUpdateBlocking(context)
    onResult(result)
  }
}
