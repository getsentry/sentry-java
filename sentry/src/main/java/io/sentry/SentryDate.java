package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SentryDate implements Comparable<SentryDate> {

  /** Returns the date in nanoseconds as long. */
  public abstract long nanoTimestamp();

  /**
   * Calculates a date by using another date.
   *
   * <p>This is a workaround for limited precision offered in some cases (e.g. when using {@link
   * SentryNanotimeDate}). This makes it possible to have high precision duration by using
   * nanoseconds for the finish timestamp where normally the start and finish timestamps would only
   * offer millisecond precision.
   *
   * @param otherDate another {@link SentryDate}
   * @return date in seconds as long
   */
  public long laterDateNanosTimestampByDiff(final @Nullable SentryDate otherDate) {
    if (otherDate != null && compareTo(otherDate) < 0) {
      return otherDate.nanoTimestamp();
    } else {
      return nanoTimestamp();
    }
  }

  /**
   * Difference between two dates in nanoseconds.
   *
   * @param otherDate another {@link SentryDate}
   * @return difference in nanoseconds
   */
  public long diff(final @NotNull SentryDate otherDate) {
    return nanoTimestamp() - otherDate.nanoTimestamp();
  }

  @Override
  public int compareTo(@NotNull SentryDate otherDate) {
    return Long.valueOf(nanoTimestamp()).compareTo(otherDate.nanoTimestamp());
  }
}
