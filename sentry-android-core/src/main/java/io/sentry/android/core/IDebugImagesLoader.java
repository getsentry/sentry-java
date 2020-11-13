package io.sentry.android.core;

import io.sentry.protocol.DebugImage;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;

/** Used for loading the list of debug images from sentry-native. */
@ApiStatus.Internal
public interface IDebugImagesLoader {
  List<DebugImage> getDebugImages();

  void clearDebugImages();
}
