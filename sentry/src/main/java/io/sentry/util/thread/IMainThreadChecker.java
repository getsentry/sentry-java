package io.sentry.util.thread;

import io.sentry.protocol.SentryThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IMainThreadChecker {

  boolean isMainThread(final long threadId);

  /**
   * Checks if a given thread is the Main/UI thread
   *
   * @param thread the Thread
   * @return true if it is the main thread or false otherwise
   */
  default boolean isMainThread(Thread thread) {
    return isMainThread(thread.getId());
  }

  /**
   * Checks if the calling/current thread is the Main/UI thread
   *
   * @return true if it is the main thread or false otherwise
   */
  default boolean isMainThread() {
    return isMainThread(Thread.currentThread());
  }

  /**
   * Checks if a given thread is the Main/UI thread
   *
   * @param sentryThread the SentryThread
   * @return true if it is the main thread or false otherwise
   */
  default boolean isMainThread(final @NotNull SentryThread sentryThread) {
    final Long threadId = sentryThread.getId();
    return threadId != null && isMainThread(threadId);
  }
}
