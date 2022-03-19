package io.sentry.android.core;

import android.os.Build;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** The Android Impl. of IBuildInfoProvider which returns the Build class info. */
@ApiStatus.Internal
public final class BuildInfoProvider {

  /**
   * Returns the Build.VERSION.SDK_INT
   *
   * @return the Build.VERSION.SDK_INT
   */
  public int getSdkInfoVersion() {
    return Build.VERSION.SDK_INT;
  }

  public @Nullable String getBuildTags() {
    return Build.TAGS;
  }
}
