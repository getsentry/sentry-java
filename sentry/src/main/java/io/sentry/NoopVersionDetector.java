package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NoopVersionDetector implements IVersionDetector {

  private static final NoopVersionDetector instance = new NoopVersionDetector();

  private NoopVersionDetector() {}

  public static NoopVersionDetector getInstance() {
    return instance;
  }

  @Override
  public boolean checkForMixedVersions() {
    return false;
  }
}
