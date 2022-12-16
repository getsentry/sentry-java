package io.sentry.util.thread;

import org.jetbrains.annotations.ApiStatus;

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
}
