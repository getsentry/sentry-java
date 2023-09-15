package io.sentry.hints;

public interface DiskFlushNotification {
  void markFlushed();

  boolean isFlushable();

  void setFlushable();
}
