package io.sentry.android.ndk;

import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.IDebugImagesLoader;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.ndk.NativeModuleListLoader;
import io.sentry.protocol.DebugImage;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

  /**
   * we need to lock it because it could be called from different threads
   */
  protected static final @NotNull AutoClosableReentrantLock debugImagesLock =
      new AutoClosableReentrantLock();

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
    try (final @NotNull ISentryLifecycleToken ignored = debugImagesLock.acquire()) {
      if (debugImages == null) {
        try {
          final io.sentry.ndk.DebugImage[] debugImagesArr = moduleListLoader.loadModuleList();
          if (debugImagesArr != null) {
            debugImages = new ArrayList<>(debugImagesArr.length);
            for (io.sentry.ndk.DebugImage d : debugImagesArr) {
              final DebugImage debugImage = new DebugImage();
              debugImage.setUuid(d.getUuid());
              debugImage.setType(d.getType());
              debugImage.setDebugId(d.getDebugId());
              debugImage.setCodeId(d.getCodeId());
              debugImage.setImageAddr(d.getImageAddr());
              debugImage.setImageSize(d.getImageSize());
              debugImage.setArch(d.getArch());
              debugImages.add(debugImage);
            }
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

  /**
   * Finds a debug image containing the given address using binary search.
   * Requires the images to be sorted.
   *
   * @return The matching debug image or null if not found
   */
  private @Nullable DebugImage findImageByAddress(long address, List<DebugImage> images) {
    int left = 0;
    int right = images.size() - 1;

    while (left <= right) {
      int mid = left + (right - left) / 2;
      DebugImage image = images.get(mid);

      if (image.getImageAddr() == null || image.getImageSize() == null) {
        return null;
      }

      long imageStart = Long.parseLong(image.getImageAddr().replace("0x", ""), 16);
      long imageEnd = imageStart + image.getImageSize();

      if (address >= imageStart && address < imageEnd) {
        return image;
      } else if (address < imageStart) {
        right = mid - 1;
      } else {
        left = mid + 1;
      }
    }

    return null;
  }

  /**
   * Loads debug images for the given set of addresses.
   * @param addresses Set of memory addresses to find debug images for
   * @return Set of debug images, or null if debug images couldn't be loaded
   */
  public @Nullable Set<DebugImage> loadDebugImagesForAddresses(@NotNull Set<Long> addresses) {
    List<DebugImage> allDebugImages = loadDebugImages();
    if (allDebugImages == null) {
      return null;
    }

    Set<DebugImage> relevantImages = new LinkedHashSet<>();
    for (Long addr : addresses) {
      DebugImage image = findImageByAddress(addr, allDebugImages);
      if (image != null) {
        relevantImages.add(image);
      }
    }

    // Return null if no images were found
    if (relevantImages.isEmpty()) {
      options.getLogger().log(
        SentryLevel.WARNING,
        "No debug images found for any of the %d addresses.",
        addresses.size()
      );
      return null;
    }

    return relevantImages;
  }

  /**
   * Clears the caching of debug images on sentry-native and here.
   */
  @Override
  public void clearDebugImages() {
    try (final @NotNull ISentryLifecycleToken ignored = debugImagesLock.acquire()) {
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
