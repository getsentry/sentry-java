package io.sentry.android.core.util;

import android.content.res.Configuration;
import io.sentry.protocol.Device;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DeviceOrientations {
  private DeviceOrientations() {}

  /**
   * Get the device's current screen orientation.
   *
   * @return the device's current screen orientation, or null if unknown
   */
  @SuppressWarnings("deprecation")
  public static @Nullable Device.DeviceOrientation getOrientation(int orientation) {
    switch (orientation) {
      case Configuration.ORIENTATION_LANDSCAPE:
        return Device.DeviceOrientation.LANDSCAPE;
      case Configuration.ORIENTATION_PORTRAIT:
        return Device.DeviceOrientation.PORTRAIT;
      case Configuration.ORIENTATION_SQUARE:
      case Configuration.ORIENTATION_UNDEFINED:
      default:
        return null;
    }
  }
}
