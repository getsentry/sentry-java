package io.sentry.android.core.internal.util;

import android.os.Looper;
import io.sentry.protocol.SentryThread;
import io.sentry.util.thread.IMainThreadChecker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Class that checks if a given thread is the Android Main/UI thread */
@ApiStatus.Internal
public final class AndroidMainThreadChecker implements IMainThreadChecker {

  private static final AndroidMainThreadChecker instance = new AndroidMainThreadChecker();

  public static AndroidMainThreadChecker getInstance() {
    return instance;
  }

  private AndroidMainThreadChecker() {}

  @Override
  public boolean isMainThread(final long threadId) {
    return Looper.getMainLooper().getThread().getId() == threadId;
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
}
