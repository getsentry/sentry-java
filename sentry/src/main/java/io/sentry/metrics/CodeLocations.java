package io.sentry.metrics;

import io.sentry.protocol.SentryStackFrame;
import java.util.Calendar;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Represents a collection of code locations. */
@ApiStatus.Internal
public final class CodeLocations {

  private final @NotNull Calendar date;
  private final @NotNull Map<MetricResourceIdentifier, SentryStackFrame> locations;

  public CodeLocations(
      final @NotNull Calendar date,
      final @NotNull Map<MetricResourceIdentifier, SentryStackFrame> locations) {
    this.date = date;
    this.locations = locations;
  }

  @NotNull
  public Calendar getDate() {
    return date;
  }

  @NotNull
  public Map<MetricResourceIdentifier, SentryStackFrame> getLocations() {
    return locations;
  }
}
