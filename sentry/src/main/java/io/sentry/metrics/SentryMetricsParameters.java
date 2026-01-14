package io.sentry.metrics;

import io.sentry.Hint;
import io.sentry.SentryAttributes;
import io.sentry.SentryDate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryMetricsParameters {

  private @Nullable SentryDate timestamp;
  private @Nullable SentryAttributes attributes;
  private @NotNull String origin = "manual";

  private @Nullable Hint hint = null;

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

  public @Nullable Hint getHint() {
    return hint;
  }

  public void setHint(final @Nullable Hint hint) {
    this.hint = hint;
  }

  public static @NotNull SentryMetricsParameters create(
      final @Nullable SentryDate timestamp, final @Nullable SentryAttributes attributes) {
    final @NotNull SentryMetricsParameters params = new SentryMetricsParameters();

    params.setTimestamp(timestamp);
    params.setAttributes(attributes);

    return params;
  }

  public static @NotNull SentryMetricsParameters create(
      final @Nullable SentryAttributes attributes) {
    return create(null, attributes);
  }
}
