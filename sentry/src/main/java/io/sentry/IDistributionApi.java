package io.sentry;

import java.util.concurrent.Future;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * API for Sentry Build Distribution functionality.
 *
 * <p>Provides methods to check for app updates and download new versions from Sentry's preprod
 * artifacts system.
 */
@ApiStatus.Experimental
public interface IDistributionApi {

  /**
   * Check for available updates synchronously (blocking call). This method will block the calling
   * thread while making the network request. Consider using checkForUpdate for non-blocking
   * behavior.
   *
   * @return UpdateStatus indicating if an update is available, up to date, or error
   */
  @NotNull
  UpdateStatus checkForUpdateBlocking();

  /**
   * Check for available updates asynchronously.
   *
   * @return Future that will resolve to an UpdateStatus result
   */
  @NotNull
  Future<UpdateStatus> checkForUpdate();

  /**
   * Download and install the provided update by opening the download URL in the default browser or
   * appropriate application.
   *
   * @param info Information about the update to download
   */
  void downloadUpdate(@NotNull UpdateInfo info);

  /**
   * Check if the distribution integration is enabled.
   *
   * @return true if the distribution integration is enabled, false otherwise
   */
  boolean isEnabled();
}
