package io.sentry.android.core;

import android.os.Build;
import org.jetbrains.annotations.ApiStatus;

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
}
