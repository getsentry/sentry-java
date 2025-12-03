package io.sentry.android.core.anr;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Coordinates file rotation between AnrProfilingIntegration and AnrV2Integration to prevent
 * concurrent access to the same QueueFile.
 */
@ApiStatus.Internal
public class AnrProfileRotationHelper {

  private static final String CURRENT_FILE_NAME = "anr_profile";
  private static final String OLD_FILE_NAME = "anr_profile_old";

  private static final AtomicBoolean shouldRotate = new AtomicBoolean(false);
  private static final Object rotationLock = new Object();

  public static void rotate() {
    shouldRotate.set(true);
  }

  private static void performRotationIfNeeded(final @NotNull File cacheDir) {
    if (!shouldRotate.get()) {
      return;
    }

    synchronized (rotationLock) {
      if (!shouldRotate.get()) {
        return;
      }

      final File currentFile = new File(cacheDir, CURRENT_FILE_NAME);
      final File oldFile = new File(cacheDir, OLD_FILE_NAME);

      if (oldFile.exists()) {
        oldFile.delete();
      }

      if (currentFile.exists()) {
        currentFile.renameTo(oldFile);
      }

      shouldRotate.set(false);
    }
  }

  @NotNull
  public static File getCurrentFile(final @NotNull File cacheDir) {
    performRotationIfNeeded(cacheDir);
    return new File(cacheDir, CURRENT_FILE_NAME);
  }

  @NotNull
  public static File getLastFile(final @NotNull File cacheDir) {
    performRotationIfNeeded(cacheDir);
    return new File(cacheDir, OLD_FILE_NAME);
  }

  public static boolean deleteLastFile(final @NotNull File cacheDir) {
    final File oldFile = new File(cacheDir, OLD_FILE_NAME);
    return oldFile.exists() && oldFile.delete();
  }
}
