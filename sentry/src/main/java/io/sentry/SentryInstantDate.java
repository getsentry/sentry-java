package io.sentry;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * This class uses {@link Instant} to provide timestamps.
 *
 * <p>NOTE: This should only be used on Java 9+ and Android API 26+
 */
@SuppressWarnings("NewApi")
public final class SentryInstantDate extends SentryDate {
  private final @NotNull Instant date;

  public SentryInstantDate() {
    this(Instant.now());
  }

  public SentryInstantDate(final @NotNull Instant date) {
    this.date = date;
  }

  @Override
  public long nanoTimestamp() {
    return DateUtils.secondsToNanos(date.getEpochSecond()) + date.getNano();
  }
}
