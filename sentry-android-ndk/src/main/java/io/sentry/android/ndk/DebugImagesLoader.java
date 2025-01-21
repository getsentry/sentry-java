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
import java.util.HashSet;
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

  private static volatile @Nullable List<DebugImage> debugImages;

  /** we need to lock it because it could be called from different threads */
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
              debugImage.setCodeFile(d.getCodeFile());
              debugImage.setDebugFile(d.getDebugFile());
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
   * Loads debug images for the given set of addresses.
   *
   * @param addresses Set of memory addresses to find debug images for
   * @return Set of matching debug images, or null if debug images couldn't be loaded
   */
  public @Nullable Set<DebugImage> loadDebugImagesForAddresses(
      final @NotNull Set<String> addresses) {
    try (final @NotNull ISentryLifecycleToken ignored = debugImagesLock.acquire()) {
      final @Nullable List<DebugImage> allDebugImages = loadDebugImages();
      if (allDebugImages == null) {
        return null;
      }
      if (addresses.isEmpty()) {
        return null;
      }

      final Set<DebugImage> referencedImages = filterImagesByAddresses(allDebugImages, addresses);
      if (referencedImages.isEmpty()) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "No debug images found for any of the %d addresses.",
                addresses.size());
        return null;
      }

      return referencedImages;
    }
  }

  /**
   * Finds a debug image containing the given address using binary search. Requires the images to be
   * sorted.
   *
   * @return The matching debug image or null if not found
   */
  private @NotNull Set<DebugImage> filterImagesByAddresses(
      final @NotNull List<DebugImage> images, final @NotNull Set<String> addresses) {
    final Set<DebugImage> result = new HashSet<>();

    for (int i = 0; i < images.size(); i++) {
      final @NotNull DebugImage image = images.get(i);
      final @Nullable DebugImage nextDebugImage =
          (i + 1) < images.size() ? images.get(i + 1) : null;
      final @Nullable String nextDebugImageAddress =
          nextDebugImage != null ? nextDebugImage.getImageAddr() : null;

      for (final @NotNull String rawAddress : addresses) {
        try {
          final long address = Long.parseLong(rawAddress.replace("0x", ""), 16);

          final @Nullable String imageAddress = image.getImageAddr();
          if (imageAddress != null) {
            try {
              final long imageStart = Long.parseLong(imageAddress.replace("0x", ""), 16);
              final long imageEnd;

              final @Nullable Long imageSize = image.getImageSize();
              if (imageSize != null) {
                imageEnd = imageStart + imageSize;
              } else if (nextDebugImageAddress != null) {
                imageEnd = Long.parseLong(nextDebugImageAddress.replace("0x", ""), 16);
              } else {
                imageEnd = Long.MAX_VALUE;
              }
              if (address >= imageStart && address < imageEnd) {
                result.add(image);
                // once image is added we can skip the remaining addresses and go straight to the
                // next
                // image
                break;
              }
            } catch (NumberFormatException e) {
              // ignored, invalid debug image address
            }
          }
        } catch (NumberFormatException e) {
          // ignored, invalid address supplied
        }
      }
    }
    return result;
  }

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
