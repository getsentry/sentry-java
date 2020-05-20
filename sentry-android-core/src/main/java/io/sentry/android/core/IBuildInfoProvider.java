package io.sentry.android.core;

/** To make SDK info classes testable */
public interface IBuildInfoProvider {

  /**
   * Returns the SDK version of the given SDK
   *
   * @return the SDK Version
   */
  int getSdkInfoVersion();
}
