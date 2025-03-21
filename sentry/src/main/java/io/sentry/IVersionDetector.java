package io.sentry;

public interface IVersionDetector {

  /**
   * Checks whether all installed Sentry Java SDK dependencies have the same version.
   *
   * @return true if mixed versions have been detected, false if all versions align
   */
  boolean checkForMixedVersions();
}
