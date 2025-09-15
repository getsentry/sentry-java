package io.sentry.android.distribution

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryOptions

/**
 * The public Android SDK for Sentry Build Distribution.
 *
 * Provides functionality to check for app updates and download new versions from Sentry's preprod
 * artifacts system.
 */
public class DistributionIntegration(context: Context) : Integration {

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
    // Distribution integration is registered but initialization still requires manual call to
    // init()
    // This allows the integration to be discovered by Sentry's auto-discovery mechanism
    // while maintaining explicit control over when distribution functionality is enabled
  }

  /**
   * Initialize build distribution with default options. This should be called once per process,
   * typically in Application.onCreate().
   *
   * @param context Android context
   */
  public fun init() {
    init {}
  }

  /**
   * Initialize build distribution with the provided configuration. This should be called once per
   * process, typically in Application.onCreate().
   *
   * @param context Android context
   * @param configuration Configuration handler for build distribution options
   */
  public fun init(configuration: (DistributionOptions) -> Unit) {
    val options = DistributionOptions()
    configuration(options)
  }

  /**
   * Check for available updates synchronously (blocking call). This method will block the calling
   * thread while making the network request. Consider using checkForUpdate with callback for
   * non-blocking behavior.
   *
   * @param context Android context
   * @return UpdateStatus indicating if an update is available, up to date, or error
   */
  public fun checkForUpdateBlocking(): UpdateStatus {
    throw NotImplementedError()
  }

  /**
   * Check for available updates asynchronously using a Kotlin lambda callback.
   *
   * @param context Android context
   * @param onResult Lambda that will be called with the UpdateStatus result
   */
  public fun checkForUpdate(onResult: (UpdateStatus) -> Unit) {
    // TODO implement this in a async way
    val result = checkForUpdateBlocking()
    onResult(result)
  }

  /**
   * Download and install the provided update by opening the download URL in the default browser or
   * appropriate application.
   *
   * @param context Android context
   * @param info Information about the update to download
   */
  public fun downloadUpdate(info: UpdateInfo) {
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
