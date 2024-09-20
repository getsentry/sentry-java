package io.sentry.android.core.internal.util;

import android.os.Looper;
import android.os.Process;
import io.sentry.protocol.SentryThread;
import io.sentry.util.thread.IThreadChecker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Class that checks if a given thread is the Android Main/UI thread */
@ApiStatus.Internal
public final class AndroidThreadChecker implements IThreadChecker {

  private static final AndroidThreadChecker instance = new AndroidThreadChecker();
  public static final long mainThreadId = Process.myTid();

  public static AndroidThreadChecker getInstance() {
    return instance;
  }

  private AndroidThreadChecker() {}

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

  @Override
  public long currentThreadId() {
    return Process.myTid();
  }
}
