package io.sentry.android.distribution

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.sentry.IDistributionApi
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryOptions
import io.sentry.UpdateInfo
import io.sentry.UpdateStatus
import org.jetbrains.annotations.ApiStatus

/**
 * The public Android SDK for Sentry Build Distribution.
 *
 * Provides functionality to check for app updates and download new versions from Sentry's preprod
 * artifacts system.
 */
@ApiStatus.Experimental
public class DistributionIntegration(context: Context) : Integration, IDistributionApi {

  private lateinit var scopes: IScopes
  private lateinit var sentryOptions: SentryOptions
  private val context: Context = context.applicationContext

  /**
   * Registers the Distribution integration with Sentry.
   *
   * @param scopes the Scopes
   * @param options the options
   */
  public override fun register(scopes: IScopes, options: SentryOptions) {
    // Store scopes and options for use by distribution functionality
    this.scopes = scopes
    this.sentryOptions = options
  }

  /**
   * Check for available updates synchronously (blocking call). This method will block the calling
   * thread while making the network request. Consider using checkForUpdate with callback for
   * non-blocking behavior.
   *
   * @return UpdateStatus indicating if an update is available, up to date, or error
   */
  public override fun checkForUpdateBlocking(): UpdateStatus {
    throw NotImplementedError()
  }

  /**
   * Check for available updates asynchronously using a callback.
   *
   * @param onResult Callback that will be called with the UpdateStatus result
   */
  public override fun checkForUpdate(onResult: IDistributionApi.UpdateCallback) {
    // TODO implement this in a async way
    val result = checkForUpdateBlocking()
    onResult.onResult(result)
  }

  /**
   * Download and install the provided update by opening the download URL in the default browser or
   * appropriate application.
   *
   * @param info Information about the update to download
   */
  public override fun downloadUpdate(info: UpdateInfo) {
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
