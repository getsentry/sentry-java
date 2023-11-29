package io.sentry.hints;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DiskFlushNotification {
  void markFlushed();

  boolean isFlushable(@Nullable SentryId eventId);

  void setFlushable(@NotNull SentryId eventId);
}
