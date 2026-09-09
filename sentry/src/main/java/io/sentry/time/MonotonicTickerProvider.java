package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies the {@link MonotonicTicker} to measure elapsed time with.
 *
 * <p>{@code SentryOptions} implements this, and the Android module overrides it with a ticker the
 * core module cannot reference. Depending on this interface rather than on the options class keeps
 * the concrete ticker out of call sites, and lets a test supply its own without subclassing the
 * final {@code SentryAndroidOptions}.
 */
@ApiStatus.Internal
public interface MonotonicTickerProvider {
  @NotNull
  MonotonicTicker getMonotonicTicker();
}
