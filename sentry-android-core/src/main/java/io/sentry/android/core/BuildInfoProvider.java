package io.sentry.android.core;

import android.os.Build;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** The Android Impl. of IBuildInfoProvider which returns the Build class info. */
@ApiStatus.Internal
public final class BuildInfoProvider implements IBuildInfoProvider {

  /**
   * Returns the Build.VERSION.SDK_INT
   *
   * @return the Build.VERSION.SDK_INT
   */
  @Override
  public int getSdkInfoVersion() {
    return Build.VERSION.SDK_INT;
  }

  @Override
  public @Nullable String getBuildTags() {
    return Build.TAGS;
  }

  @Override
  public @Nullable String getManufacturer() {
    return Build.MANUFACTURER;
  }

  @Override
  public @Nullable String getModel() {
    return Build.MODEL;
  }

  @Override
  public @Nullable String getVersionRelease() {
    return Build.VERSION.RELEASE;
  }
}
