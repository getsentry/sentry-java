package io.sentry.metrics;

import io.sentry.protocol.SentryStackFrame;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Represents a collection of code locations, taken at a specific time */
@ApiStatus.Internal
public final class CodeLocations {

  private final double timestamp;
  private final @NotNull Map<MetricResourceIdentifier, SentryStackFrame> locations;

  public CodeLocations(
      final double date, final @NotNull Map<MetricResourceIdentifier, SentryStackFrame> locations) {
    this.timestamp = date;
    this.locations = locations;
  }

  @NotNull
  public double getTimestamp() {
    return timestamp;
  }

  @NotNull
  public Map<MetricResourceIdentifier, SentryStackFrame> getLocations() {
    return locations;
  }
}
