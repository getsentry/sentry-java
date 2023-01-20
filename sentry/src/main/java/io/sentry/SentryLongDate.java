package io.sentry;

public final class SentryLongDate extends SentryDate {
  private final long nanos;

  public SentryLongDate(final long nanos) {
    this.nanos = nanos;
  }

  @Override
  public long nanoTimestamp() {
    return nanos;
  }
}
