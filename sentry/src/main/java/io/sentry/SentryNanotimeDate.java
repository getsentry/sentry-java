package io.sentry;

import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Uses {@link Date} in combination with System.nanoTime().
 *
 * <p>A single date only offers millisecond precision but diff can be calculated with up to
 * nanosecond precision. This increased precision can also be used to calculate a new end date for a
 * transaction where start date is sent with ms precision and end date is added to it with ns
 * precision leading to an end timestamp with ns precision that can be used to gain ns precision
 * transaction durations.
 *
 * <p>This is a workaround for older versions of Java (before 9) and Android API (lower than 26)
 * that allows for higher precision than {@link Date} alone would.
 */
public final class SentryNanotimeDate extends SentryDate {

  private final @NotNull Date date;
  private final long nanos;

  public SentryNanotimeDate() {
    this(DateUtils.getCurrentDateTime(), System.nanoTime());
  }

  public SentryNanotimeDate(final @NotNull Date date, final long nanos) {
    this.date = date;
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
    return DateUtils.dateToNanos(date);
  }

  @Override
  public long laterDateNanosTimestampByDiff(final @Nullable SentryDate otherDate) {
    if (otherDate != null && otherDate instanceof SentryNanotimeDate) {
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
      final long thisDateMillis = date.getTime();
      final long otherDateMillis = otherNanoDate.date.getTime();
      if (thisDateMillis == otherDateMillis) {
        return Long.valueOf(nanos).compareTo(otherNanoDate.nanos);
      } else {
        return Long.valueOf(thisDateMillis).compareTo(otherDateMillis);
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
