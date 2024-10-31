package io.sentry.util;

import org.jetbrains.annotations.NotNull;

/**
 * This SentryRandom is a compromise used for improving performance of the SDK.
 *
 * <p>We did some testing where using Random from multiple threads degrades performance
 * significantly. We opted for this approach as it wasn't easily possible to vendor
 * ThreadLocalRandom since it's using advanced features that can cause java.lang.IllegalAccessError.
 */
public final class SentryRandom {

  private static final @NotNull SentryRandomThreadLocal instance = new SentryRandomThreadLocal();

  /**
   * Returns the current threads instance of {@link Random}. An instance of {@link Random} will be
   * created the first time this is invoked on each thread.
   *
   * <p>NOTE: Avoid holding a reference to the returned {@link Random} instance as sharing a
   * reference across threads (while being thread-safe) will likely degrade performance
   * significantly.
   *
   * @return random
   */
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
