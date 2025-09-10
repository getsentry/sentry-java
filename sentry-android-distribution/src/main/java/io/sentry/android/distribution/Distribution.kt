package io.sentry.android.distribution

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.sentry.android.distribution.internal.DistributionInternal

/**
 * The public Android SDK for Sentry Build Distribution.
 *
 * Provides functionality to check for app updates and download new versions from Sentry's preprod
 * artifacts system.
 */
public object Distribution {
  /**
   * Initialize build distribution with default options. This should be called once per process,
   * typically in Application.onCreate().
   *
   * @param context Android context
   */
  public fun init(context: Context) {
    init(context) {}
  }

  /**
   * Initialize build distribution with the provided configuration. This should be called once per
   * process, typically in Application.onCreate().
   *
   * @param context Android context
   * @param configuration Configuration handler for build distribution options
   */
  public fun init(context: Context, configuration: (DistributionOptions) -> Unit) {
    val options = DistributionOptions()
    configuration(options)
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
   * thread while making the network request. Consider using checkForUpdate with callback for
   * non-blocking behavior.
   *
   * @param context Android context
   * @return UpdateStatus indicating if an update is available, up to date, or error
   */
  public fun checkForUpdateBlocking(context: Context): UpdateStatus {
    return DistributionInternal.checkForUpdate(context)
  }

  /**
   * Check for available updates asynchronously using a callback.
   *
   * @param context Android context
   * @param onResult Callback that will be called with the UpdateStatus result
   */
  public fun checkForUpdate(context: Context, onResult: (UpdateStatus) -> Unit) {
    DistributionInternal.checkForUpdateAsync(context, onResult)
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

    try {
      context.startActivity(browserIntent)
    } catch (e: android.content.ActivityNotFoundException) {
      // No application can handle the HTTP/HTTPS URL, typically no browser installed
      // Silently fail as this is expected behavior in some environments
    }
  }
}
