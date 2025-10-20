package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** No-op implementation of IDistributionApi. Used when distribution module is not available. */
@ApiStatus.Experimental
public final class NoOpDistributionApi implements IDistributionApi {

  private static final NoOpDistributionApi instance = new NoOpDistributionApi();

  private NoOpDistributionApi() {}

  public static NoOpDistributionApi getInstance() {
    return instance;
  }

  @Override
  public @NotNull UpdateStatus checkForUpdateBlocking() {
    return UpdateStatus.UpToDate.getInstance();
  }

  @Override
  public void checkForUpdate(@NotNull UpdateCallback onResult) {
    // No-op implementation - do nothing
  }

  @Override
  public void downloadUpdate(@NotNull UpdateInfo info) {
    // No-op implementation - do nothing
  }

  @Override
  public boolean isEnabled() {
    return false;
  }
}
