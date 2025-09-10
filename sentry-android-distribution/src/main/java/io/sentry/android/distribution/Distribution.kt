package io.sentry.android.distribution

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.sentry.android.distribution.internal.DistributionInternal
import java.util.concurrent.CompletableFuture

/**
 * The public Android SDK for Sentry Build Distribution.
 *
 * Provides functionality to check for app updates and download new versions from Sentry's preprod
 * artifacts system.
 */
public object Distribution {
  /**
   * Initialize build distribution with the provided options. This should be called once per
   * process, typically in Application.onCreate().
   *
   * @param context Android context
   * @param options Configuration options for build distribution
   */
  public fun init(context: Context, options: DistributionOptions) {
    DistributionInternal.init(context, options)
  }

  /**
   * Check if build distribution is enabled and properly configured.
   *
   * @return true if build distribution is enabled
   */
  public fun isEnabled(): Boolean {
    return DistributionInternal.isEnabled()
  }

  /**
   * Check for available updates synchronously (blocking call). This method will block the calling
   * thread while making the network request. Consider using checkForUpdateCompletableFuture for
   * non-blocking behavior.
   *
   * @param context Android context
   * @return UpdateStatus indicating if an update is available, up to date, or error
   */
  public fun checkForUpdate(context: Context): UpdateStatus {
    return DistributionInternal.checkForUpdate(context)
  }

  /**
   * Check for available updates using CompletableFuture for Java compatibility.
   *
   * @param context Android context
   * @return CompletableFuture with UpdateStatus result
   */
  public fun checkForUpdateCompletableFuture(context: Context): CompletableFuture<UpdateStatus> {
    return DistributionInternal.checkForUpdateCompletableFuture(context)
  }

  /**
   * Download and install the provided update by opening the download URL in the default browser or
   * appropriate application.
   *
   * @param context Android context
   * @param info Information about the update to download
   */
  public fun downloadUpdate(context: Context, info: UpdateInfo) {
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(browserIntent)
  }
}
