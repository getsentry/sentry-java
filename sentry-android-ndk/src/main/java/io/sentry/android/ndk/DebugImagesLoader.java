package io.sentry.android.ndk;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.IDebugImagesLoader;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.protocol.DebugImage;
import io.sentry.util.Objects;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Class used for loading the list of debug images from sentry-native. Using this class requires
 * manually initializing the SDK.
 */
public final class DebugImagesLoader implements IDebugImagesLoader {

  private final @NotNull SentryOptions options;

  private final @NotNull NativeModuleListLoader moduleListLoader;

  private static @Nullable List<DebugImage> debugImages;

  /** we need to lock it because it could be called from different threads */
  private static final @NotNull Object debugImagesLock = new Object();

  public DebugImagesLoader(
      final @NotNull SentryAndroidOptions options,
      final @NotNull NativeModuleListLoader moduleListLoader) {
    this.options = Objects.requireNonNull(options, "The SentryAndroidOptions is required.");
    this.moduleListLoader =
        Objects.requireNonNull(moduleListLoader, "The NativeModuleListLoader is required.");
  }

  /**
   * Returns the list of debug images loaded by sentry-native.
   *
   * @return the list or null.
   */
  @Override
  public @Nullable List<DebugImage> loadDebugImages() {
    synchronized (debugImagesLock) {
      if (debugImages == null) {
        try {
          final DebugImage[] debugImagesArr = moduleListLoader.loadModuleList();
          if (debugImagesArr != null) {
            debugImages = Arrays.asList(debugImagesArr);
            options
                .getLogger()
                .log(SentryLevel.DEBUG, "Debug images loaded: %d", debugImages.size());
          }
        } catch (Throwable e) {
          options.getLogger().log(SentryLevel.ERROR, e, "Failed to load debug images.");
        }
      }
    }
    return debugImages;
  }

  /** Clears the caching of debug images on sentry-native and here. */
  @Override
  public void clearDebugImages() {
    synchronized (debugImagesLock) {
      try {
        moduleListLoader.clearModuleList();

        options.getLogger().log(SentryLevel.INFO, "Debug images cleared.");
      } catch (Throwable e) {
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
