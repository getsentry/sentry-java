package io.sentry;

import io.sentry.internal.ManifestVersionReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ManifestVersionDetector implements IVersionDetector {

  private final @NotNull SentryOptions options;

  public ManifestVersionDetector(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public boolean checkForMixedVersions() {
    ManifestVersionReader.getInstance().readManifestFiles();
    return SentryIntegrationPackageStorage.getInstance().checkForMixedVersions(options.getLogger());
  }
}
