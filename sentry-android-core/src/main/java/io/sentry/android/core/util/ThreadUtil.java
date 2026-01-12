package io.sentry.android.core.util;

import android.os.Build;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for thread-related operations that handles Android version compatibility.
 *
 * <p>In Android 16+, {@link Thread#getId()} is deprecated in favor of {@link Thread#threadId()}.
 * This class provides a version-aware method to retrieve thread IDs that works across all Android
 * versions.
 */
@ApiStatus.Internal
public final class ThreadUtil {

  private ThreadUtil() {
    // Utility class, no instances
  }

  /**
   * Gets the thread ID in a way that's compatible across Android versions.
   *
   * <p>Uses {@link Thread#threadId()} on Android 14 (API 34) and above, and falls back to {@link
   * Thread#getId()} on older versions.
   *
   * @param thread the thread to get the ID for
   * @return the thread ID
   */
  @SuppressWarnings("deprecation")
  public static long getThreadId(final @NotNull Thread thread) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return thread.threadId();
    } else {
      return thread.getId();
    }
  }
}
