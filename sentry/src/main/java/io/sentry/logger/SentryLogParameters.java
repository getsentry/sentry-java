package io.sentry.logger;

import io.sentry.SentryAttributes;
import io.sentry.SentryDate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryLogParameters {

  private @Nullable SentryDate timestamp;
  private @Nullable SentryAttributes attributes;
  private @NotNull String origin = "manual";

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

  public @NotNull String getOrigin() {
    return origin;
  }

  public void setOrigin(final @NotNull String origin) {
    this.origin = origin;
  }

  public static @NotNull SentryLogParameters create(
      final @Nullable SentryDate timestamp, final @Nullable SentryAttributes attributes) {
    final @NotNull SentryLogParameters params = new SentryLogParameters();

    params.setTimestamp(timestamp);
    params.setAttributes(attributes);

    return params;
  }

  public static @NotNull SentryLogParameters create(final @Nullable SentryAttributes attributes) {
    return create(null, attributes);
  }
}
