package io.sentry.android.core.internal.util;

import android.os.Build;
import android.os.Handler;
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
  public static volatile long mainThreadSystemId = Process.myTid();

  public static AndroidThreadChecker getInstance() {
    return instance;
  }

  private AndroidThreadChecker() {
    // The first time this class is loaded, we make sure to set the correct mainThreadId
    new Handler(Looper.getMainLooper()).post(() -> mainThreadSystemId = Process.myTid());
  }

  /**
   * Gets the thread ID in a way that's compatible across Android versions.
   *
   * <p>Uses {@link Thread#threadId()} on Android 16 (API 36) and above, and falls back to {@link
   * Thread#getId()} on older versions.
   *
   * @param thread the thread to get the ID for
   * @return the thread ID
   */
  @SuppressWarnings("deprecation")
  public static long getThreadId(final @NotNull Thread thread) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
      return thread.threadId();
    } else {
      return thread.getId();
    }
  }

  @Override
  public boolean isMainThread(final long threadId) {
    return getThreadId(Looper.getMainLooper().getThread()) == threadId;
  }

  @Override
  public boolean isMainThread(final @NotNull Thread thread) {
    return isMainThread(getThreadId(thread));
  }

  @Override
  public boolean isMainThread() {
    return isMainThread(Thread.currentThread());
  }

  @Override
  public @NotNull String getCurrentThreadName() {
    return isMainThread() ? "main" : Thread.currentThread().getName();
  }

  @Override
  public boolean isMainThread(final @NotNull SentryThread sentryThread) {
    final Long threadId = sentryThread.getId();
    return threadId != null && isMainThread(threadId);
  }

  @Override
  public long currentThreadSystemId() {
    return Process.myTid();
  }
}
