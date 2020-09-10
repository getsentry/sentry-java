package io.sentry.android.core.util;

import android.os.Looper;
import io.sentry.protocol.SentryThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Class that checks if a given thread is the Android Main/UI thread */
@ApiStatus.Internal
public final class MainThreadChecker {

  private MainThreadChecker() {}

  /**
   * Checks if a given thread is the Android Main/UI thread
   *
   * @param thread the Thread
   * @return true if it is the main thread or false otherwise
   */
  public static boolean isMainThread(final @NotNull Thread thread) {
    return isMainThread(thread.getId());
  }

  /**
   * Checks if the calling/current thread is the Android Main/UI thread
   *
   * @return true if it is the main thread or false otherwise
   */
  public static boolean isMainThread() {
    return isMainThread(Thread.currentThread());
  }

  /**
   * Checks if a given thread is the Android Main/UI thread
   *
   * @param sentryThread the SentryThread
   * @return true if it is the main thread or false otherwise
   */
  public static boolean isMainThread(final @NotNull SentryThread sentryThread) {
    return isMainThread(sentryThread.getId());
  }

  private static boolean isMainThread(final long threadId) {
    return Looper.getMainLooper().getThread().getId() == threadId;
  }
}
