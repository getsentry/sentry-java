package io.sentry;

import org.jetbrains.annotations.NotNull;

/**
 * API for Sentry Build Distribution functionality.
 *
 * <p>Provides methods to check for app updates and download new versions from Sentry's preprod
 * artifacts system.
 */
public interface IDistributionApi {

  /**
   * Check for available updates synchronously (blocking call). This method will block the calling
   * thread while making the network request. Consider using checkForUpdate with callback for
   * non-blocking behavior.
   *
   * @return UpdateStatus indicating if an update is available, up to date, or error
   */
  @NotNull
  Object checkForUpdateBlocking();

  /**
   * Check for available updates asynchronously using a callback.
   *
   * @param onResult Callback that will be called with the UpdateStatus result
   */
  void checkForUpdate(@NotNull Object onResult);

  /**
   * Download and install the provided update by opening the download URL in the default browser or
   * appropriate application.
   *
   * @param info Information about the update to download
   */
  void downloadUpdate(@NotNull Object info);
}
