package io.sentry.android.core;

import io.sentry.protocol.DebugImage;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Used for loading the list of debug images from sentry-native. */
@ApiStatus.Internal
public interface IDebugImagesLoader {
  @Nullable
  List<DebugImage> loadDebugImages();

  @Nullable
  Set<DebugImage> loadDebugImagesForAddresses(Set<Long> addresses);

  void clearDebugImages();
}
