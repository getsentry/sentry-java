package io.sentry.android.distribution

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import io.sentry.IDistributionApi
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.UpdateInfo
import io.sentry.UpdateStatus
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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

  private lateinit var httpClient: DistributionHttpClient
  private lateinit var responseParser: UpdateResponseParser

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

    // Initialize HTTP client and response parser
    this.httpClient = DistributionHttpClient(options)
    this.responseParser = UpdateResponseParser(options)
  }

  /**
   * Check for available updates synchronously (blocking call). This method will block the calling
   * thread while making the network request. Consider using checkForUpdate with callback for
   * non-blocking behavior.
   *
   * @return UpdateStatus indicating if an update is available, up to date, or error
   */
  public override fun checkForUpdateBlocking(): UpdateStatus {
    return try {
      sentryOptions.logger.log(SentryLevel.DEBUG, "Checking for distribution updates")

      val params = createUpdateCheckParams()
      val response = httpClient.checkForUpdates(params)
      responseParser.parseResponse(response.statusCode, response.body)
    } catch (e: IllegalStateException) {
      sentryOptions.logger.log(SentryLevel.WARNING, e.message ?: "Configuration error")
      UpdateStatus.UpdateError(e.message ?: "Configuration error")
    } catch (e: UnknownHostException) {
      // UnknownHostException typically indicates no internet connection available
      sentryOptions.logger.log(
        SentryLevel.ERROR,
        e,
        "DNS lookup failed - check internet connection",
      )
      UpdateStatus.NoNetwork("No internet connection or invalid server URL")
    } catch (e: SocketTimeoutException) {
      // SocketTimeoutException could indicate either slow network or server issues
      sentryOptions.logger.log(SentryLevel.ERROR, e, "Network request timed out")
      UpdateStatus.NoNetwork("Request timed out - check network connection")
    } catch (e: Exception) {
      sentryOptions.logger.log(SentryLevel.ERROR, e, "Unexpected error checking for updates")
      UpdateStatus.UpdateError("Unexpected error: ${e.message}")
    }
  }

  /**
   * Check for available updates asynchronously using a callback.
   *
   * Note: The callback will be invoked on a background thread. If you need to update UI or perform
   * main-thread operations, dispatch the result to the main thread yourself.
   *
   * @param onResult Callback that will be called with the UpdateStatus result
   */
  public override fun checkForUpdate(onResult: IDistributionApi.UpdateCallback) {
    Thread {
        val result = checkForUpdateBlocking()
        onResult.onResult(result)
      }
      .start()
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

  /**
   * Check if the distribution integration is enabled.
   *
   * @return true if the distribution integration is enabled
   */
  public override fun isEnabled(): Boolean {
    return true
  }

  private fun createUpdateCheckParams(): DistributionHttpClient.UpdateCheckParams {
    return try {
      val packageManager = context.packageManager
      val packageName = context.packageName
      val packageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
          @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
        }

      val versionName = packageInfo.versionName ?: "unknown"
      val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          packageInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        }
      val appId = context.applicationInfo.packageName

      val buildConfiguration =
        sentryOptions.distribution.buildConfiguration
          ?: throw IllegalStateException("buildConfiguration must be set in distribution options")

      DistributionHttpClient.UpdateCheckParams(
        appId = appId,
        platform = "android",
        versionCode = versionCode,
        versionName = versionName,
        buildConfiguration = buildConfiguration,
      )
    } catch (e: PackageManager.NameNotFoundException) {
      sentryOptions.logger.log(SentryLevel.ERROR, e, "Failed to get package info")
      throw IllegalStateException("Unable to get app package information", e)
    }
  }
}
