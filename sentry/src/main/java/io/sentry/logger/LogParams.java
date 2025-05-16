package io.sentry.logger;

import io.sentry.SentryAttributes;
import io.sentry.SentryDate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LogParams {

  private @Nullable SentryDate timestamp;
  private @Nullable SentryAttributes attributes;

  public @Nullable SentryDate getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final @Nullable SentryDate timestamp) {
    this.timestamp = timestamp;
  }

  public @Nullable SentryAttributes getAttributes() {
    return attributes;
  }

  public void setAttributes(final @Nullable SentryAttributes attributes) {
    this.attributes = attributes;
  }

  public static @NotNull LogParams create(
      final @Nullable SentryDate timestamp, final @Nullable SentryAttributes attributes) {
    final @NotNull LogParams params = new LogParams();

    params.setTimestamp(timestamp);
    params.setAttributes(attributes);

    return params;
  }

  public static @NotNull LogParams create(final @Nullable SentryAttributes attributes) {
    return create(null, attributes);
  }
}
