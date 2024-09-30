package io.sentry.util.thread;

import io.sentry.protocol.SentryThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Class that checks if a given thread is the Main/UI thread. The Main thread is denoted by the
 * thread, where the `Sentry.init` method was called. If it was inited from a background thread,
 * then this class is wrong, but it's a best effort.
 *
 * <p>We're gonna educate people through the docs.
 */
@ApiStatus.Internal
public final class ThreadChecker implements IThreadChecker {

  private static final long mainThreadId = Thread.currentThread().getId();
  private static final ThreadChecker instance = new ThreadChecker();

  public static ThreadChecker getInstance() {
    return instance;
  }

  private ThreadChecker() {}

  @Override
  public boolean isMainThread(long threadId) {
    return mainThreadId == threadId;
  }

  @Override
  public boolean isMainThread(final @NotNull Thread thread) {
    return isMainThread(thread.getId());
  }

  @Override
  public boolean isMainThread() {
    return isMainThread(Thread.currentThread());
  }

  @Override
  public boolean isMainThread(final @NotNull SentryThread sentryThread) {
    final Long threadId = sentryThread.getId();
    return threadId != null && isMainThread(threadId);
  }

  @Override
  public long currentThreadSystemId() {
    return Thread.currentThread().getId();
  }
}
