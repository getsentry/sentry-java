package io.sentry.time;

import io.sentry.DateUtils;
import java.time.Instant;
import org.jetbrains.annotations.ApiStatus;

/**
 * Reads the epoch from {@link Instant}.
 *
 * <p>A class of its own so the reference to {@code java.time} is loaded only where {@link
 * SystemEpochClock} decided to use it. Android's minSdk is below the API 26 that introduced {@code
 * Instant}.
 */
@ApiStatus.Internal
@SuppressWarnings("NewApi")
final class InstantEpochNanos {

  private InstantEpochNanos() {}

  static long read() {
    final Instant now = Instant.now();
    // No long overflow until year 2262
    return DateUtils.secondsToNanos(now.getEpochSecond()) + now.getNano();
  }
}
