package io.sentry.util;

import org.jetbrains.annotations.NotNull;

public final class SentryRandom {

  private static final SentryRandomThreadLocal instance = new SentryRandomThreadLocal();

  public static @NotNull Random current() {
    return instance.get();
  }

  private static class SentryRandomThreadLocal extends ThreadLocal<Random> {

    @Override
    protected Random initialValue() {
      return new Random();
    }
  }
}
