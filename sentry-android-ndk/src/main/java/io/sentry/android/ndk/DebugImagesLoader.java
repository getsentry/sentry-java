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
import org.jetbrains.annotations.VisibleForTesting;

final class DebugImagesLoader implements IDebugImagesLoader {

  private final @NotNull SentryOptions options;

  private final @NotNull IModuleListLoader moduleListLoader;

  private static @Nullable List<DebugImage> debugImages;

  private static final @NotNull Object debugImagesLock = new Object();

  DebugImagesLoader(
      final @NotNull SentryOptions options, final @NotNull IModuleListLoader moduleListLoader) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
    this.moduleListLoader =
        Objects.requireNonNull(moduleListLoader, "The ModuleListLoader is required.");
  }

  /**
   * Returns the list of debug images loaded by sentry-native. If NDK is disabled this is a NoOp and
   * it returns null;
   *
   * @return the list or null.
   */
  @Override
  public @Nullable List<DebugImage> getDebugImages() {
    if (!options.isEnableNdk()) {
      return null;
    }

    synchronized (debugImagesLock) {
      if (debugImages == null) {
        try {
          final DebugImage[] debugImagesArr = moduleListLoader.getModuleList();
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

  /**
   * Clears the caching of debug images on sentry-native and here. If NDK is disabled this is a
   * NoOp.
   */
  @Override
  public void clearDebugImages() {
    if (!options.isEnableNdk()) {
      return;
    }

    synchronized (debugImagesLock) {
      try {
        moduleListLoader.clearModuleList();

        options.getLogger().log(SentryLevel.INFO, "Debug images cleared.");
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, e, "Failed to clear debug images.");
      }
      debugImages = null;
    }
  }

  @VisibleForTesting
  @Nullable
  List<DebugImage> getCachedDebugImages() {
    return debugImages;
  }
}
