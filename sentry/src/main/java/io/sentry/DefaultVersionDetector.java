package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DefaultVersionDetector implements IVersionDetector {

  private final @NotNull SentryOptions options;

  public DefaultVersionDetector(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public boolean checkForMixedVersions() {
    return SentryIntegrationPackageStorage.getInstance().checkForMixedVersions(options.getFatalLogger());
  }
}
