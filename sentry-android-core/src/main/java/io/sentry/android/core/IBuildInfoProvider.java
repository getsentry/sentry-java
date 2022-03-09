package io.sentry.android.core;

import org.jetbrains.annotations.Nullable;

/** To make SDK info classes testable */
public interface IBuildInfoProvider {

  /**
   * Returns the SDK version of the given SDK
   *
   * @return the SDK Version
   */
  int getSdkInfoVersion();

  /**
   * Returns the Build tags of the given SDK
   *
   * @return the Build tags
   */
  @Nullable
  String getBuildTags();

  /**
   * Returns the manufacturer of the device
   *
   * @return the Manufacturer
   */
  @Nullable
  String getManufacturer();

  /**
   * Returns the model of the device
   *
   * @return the Build tags
   */
  @Nullable
  String getModel();

  /**
   * Returns the release version of the device os
   *
   * @return the Release version
   */
  @Nullable
  String getVersionRelease();
}
