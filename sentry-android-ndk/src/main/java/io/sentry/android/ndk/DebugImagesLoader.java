package io.sentry.android.ndk;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.IDebugImagesLoader;
import io.sentry.protocol.DebugImage;
import io.sentry.util.Objects;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DebugImagesLoader implements IDebugImagesLoader {

  private final @NotNull SentryOptions options;

  private static @Nullable List<DebugImage> debugImages;

  private static final @NotNull Object debugImagesLock = new Object();

  DebugImagesLoader(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
  }

  @Override
  public @Nullable List<DebugImage> getDebugImages() {
    if (!options.isEnableNdk()) {
      return null;
    }

    synchronized (debugImagesLock) {
      if (debugImages == null) {
        try {
          final DebugImage[] debugImagesArr = nativeGetModuleList();
          if (debugImagesArr != null) {
            debugImages = Arrays.asList(debugImagesArr);
            options
                .getLogger()
                .log(SentryLevel.DEBUG, "Debug images loaded: %d", debugImages.size());
          }
        } catch (Exception e) {
          options.getLogger().log(SentryLevel.ERROR, e, "Failed to load debug images.");
        }
      }
    }
    return debugImages;
  }

  @Override
  public void clearDebugImages() {
    if (!options.isEnableNdk()) {
      return;
    }

    synchronized (debugImagesLock) {
      try {
        nativeClearModuleList();

        options.getLogger().log(SentryLevel.INFO, "Debug images cleared.");
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, e, "Failed to clear debug images.");
      }
      debugImages = null;
    }
  }

  public static native DebugImage[] nativeGetModuleList();

  public static native void nativeClearModuleList();
}
