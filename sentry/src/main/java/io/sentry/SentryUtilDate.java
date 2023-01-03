package io.sentry;

import java.util.Date;
import org.jetbrains.annotations.NotNull;

public final class SentryUtilDate extends SentryDate {

  private final @NotNull Date date;

  public SentryUtilDate() {
    this(DateUtils.getCurrentDateTime());
  }

  public SentryUtilDate(final @NotNull Date date) {
    this.date = date;
  }

  @Override
  public long nanoTimestamp() {
    return DateUtils.dateToNanos(date);
  }
}
