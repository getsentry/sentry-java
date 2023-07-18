package io.sentry.util.thread;

import io.sentry.protocol.SentryThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpMainThreadChecker implements IMainThreadChecker {

  private static final NoOpMainThreadChecker instance = new NoOpMainThreadChecker();

  public static NoOpMainThreadChecker getInstance() {
    return instance;
  }

  @Override
  public boolean isMainThread(long threadId) {
    return false;
  }

  @Override public boolean isMainThread(@NotNull Thread thread) {
    return false;
  }

  @Override public boolean isMainThread() {
    return false;
  }

  @Override public boolean isMainThread(@NotNull SentryThread sentryThread) {
    return false;
  }
}
