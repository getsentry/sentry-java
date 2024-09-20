package io.sentry.util.thread;

import io.sentry.protocol.SentryThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IThreadChecker {

  boolean isMainThread(final long threadId);

  /**
   * Checks if a given thread is the Main/UI thread
   *
   * @param thread the Thread
   * @return true if it is the main thread or false otherwise
   */
  boolean isMainThread(final @NotNull Thread thread);

  /**
   * Checks if the calling/current thread is the Main/UI thread
   *
   * @return true if it is the main thread or false otherwise
   */
  boolean isMainThread();

  /**
   * Checks if a given thread is the Main/UI thread
   *
   * @param sentryThread the SentryThread
   * @return true if it is the main thread or false otherwise
   */
  boolean isMainThread(final @NotNull SentryThread sentryThread);

  /**
   * Returns the system id of the current thread. Currently only used for Android.
   *
   * @return the current thread system id.
   */
  long currentThreadSystemId();
}
