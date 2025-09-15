package io.sentry.android.distribution

import android.content.Context
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryOptions
import io.sentry.android.distribution.internal.DistributionInternal

/**
 * The public Android SDK for Sentry Build Distribution.
 *
 * Provides functionality to check for app updates and download new versions from Sentry's preprod
 * artifacts system.
 */
public class Distribution(context: Context) : Integration {
  private var scopes: IScopes? = null
  private var sentryOptions: SentryOptions? = null

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
    DistributionInternal(context, options)
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
    return DistributionInternal.checkForUpdateBlocking(context)
  }

  /**
   * Check for available updates asynchronously using a Kotlin lambda callback.
   *
   * @param context Android context
   * @param onResult Lambda that will be called with the UpdateStatus result
   */
  public fun checkForUpdate(context: Context, onResult: (UpdateStatus) -> Unit) {
    DistributionInternal.checkForUpdateAsync(context, onResult)
  }

  /**
   * Download and install the provided update by opening the download URL in the default browser or
   * appropriate application.
   *
   * @param context Android context
   * @param updateInfo Information about the update to download
   */
  public fun downloadUpdate(context: Context, updateInfo: Any) {
    if (updateInfo is UpdateInfo) {
      DistributionInternal.downloadUpdate(context, updateInfo)
    }
  }

  /**
   * Download and install the provided update by opening the download URL in the default browser or
   * appropriate application.
   *
   * @param context Android context
   * @param info Information about the update to download
   */
  public fun downloadUpdate(context: Context, info: UpdateInfo) {
    DistributionInternal.downloadUpdate(context, info)
  }
}
