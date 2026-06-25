package io.sentry;

import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Uses a unix timestamp (milliseconds since the epoch) in combination with System.nanoTime().
 *
 * <p>The unix timestamp only offers millisecond precision but diff can be calculated with up to
 * nanosecond precision. This increased precision can also be used to calculate a new end date for a
 * transaction where start date is sent with ms precision and end date is added to it with ns
 * precision leading to an end timestamp with ns precision that can be used to gain ns precision
 * transaction durations.
 *
 * <p>This is a workaround for older versions of Java (before 9) and Android API (lower than 26)
 * that allows for higher precision than a millisecond timestamp alone would.
 */
@ApiStatus.Internal
public final class SentryNanotimeDate extends SentryDate {

  private final long unixDateMillis;
  private final long nanos;

  public SentryNanotimeDate() {
    this(System.currentTimeMillis(), System.nanoTime());
  }

  /**
   * @deprecated use {@link SentryNanotimeDate#SentryNanotimeDate(long, long)} instead.
   */
  @Deprecated
  @SuppressWarnings({"InlineMeSuggester", "JavaUtilDate"})
  public SentryNanotimeDate(final @NotNull Date date, final long nanos) {
    this(date.getTime(), nanos);
  }

  public SentryNanotimeDate(final long unixDateMillis, final long nanos) {
    this.unixDateMillis = unixDateMillis;
    this.nanos = nanos;
  }

  @Override
  public long diff(final @NotNull SentryDate otherDate) {
    if (otherDate instanceof SentryNanotimeDate) {
      final @NotNull SentryNanotimeDate otherNanoDate = (SentryNanotimeDate) otherDate;
      return nanos - otherNanoDate.nanos;
    }
    return super.diff(otherDate);
  }

  @Override
  public long nanoTimestamp() {
    return DateUtils.millisToNanos(unixDateMillis);
  }

  @Override
  public long laterDateNanosTimestampByDiff(final @Nullable SentryDate otherDate) {
    if (otherDate instanceof SentryNanotimeDate) {
      final @NotNull SentryNanotimeDate otherNanoDate = (SentryNanotimeDate) otherDate;
      if (compareTo(otherDate) < 0) {
        return nanotimeDiff(this, otherNanoDate);
      } else {
        return nanotimeDiff(otherNanoDate, this);
      }
    } else {
      return super.laterDateNanosTimestampByDiff(otherDate);
    }
  }

  @Override
  @SuppressWarnings("JavaUtilDate")
  public int compareTo(@NotNull SentryDate otherDate) {
    if (otherDate instanceof SentryNanotimeDate) {
      final @NotNull SentryNanotimeDate otherNanoDate = (SentryNanotimeDate) otherDate;
      final long thisDateMillis = unixDateMillis;
      final long otherDateMillis = otherNanoDate.unixDateMillis;
      if (thisDateMillis == otherDateMillis) {
        return Long.compare(nanos, otherNanoDate.nanos);
      } else {
        return Long.compare(thisDateMillis, otherDateMillis);
      }
    } else {
      return super.compareTo(otherDate);
    }
  }

  private long nanotimeDiff(
      final @NotNull SentryNanotimeDate earlierDate, final @NotNull SentryNanotimeDate laterDate) {
    final long nanoDiff = laterDate.nanos - earlierDate.nanos;
    return earlierDate.nanoTimestamp() + nanoDiff;
  }
}
