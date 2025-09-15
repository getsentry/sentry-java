package io.sentry;

import org.jetbrains.annotations.NotNull;

/** No-op implementation of IDistributionApi. Used when distribution module is not available. */
public final class NoOpDistributionApi implements IDistributionApi {

  private static final NoOpDistributionApi instance = new NoOpDistributionApi();

  private NoOpDistributionApi() {}

  public static NoOpDistributionApi getInstance() {
    return instance;
  }

  @Override
  public @NotNull Object checkForUpdateBlocking() {
    // Return a no-op result - could be null or an error status
    return new Object(); // This will need to be properly typed when the actual types are available
  }

  @Override
  public void checkForUpdate(@NotNull Object onResult) {
    // No-op implementation - do nothing
  }

  @Override
  public void downloadUpdate(@NotNull Object info) {
    // No-op implementation - do nothing
  }
}
