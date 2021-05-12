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
}
