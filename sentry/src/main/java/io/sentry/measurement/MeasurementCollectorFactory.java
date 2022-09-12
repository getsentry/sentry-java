package io.sentry.measurement;

import io.sentry.SentryOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface MeasurementCollectorFactory {

  @NotNull
  MeasurementCollector create(
      @NotNull SentryOptions options, @NotNull MeasurementBackgroundService backgroundService);

  @Nullable
  default MeasurementBackgroundCollector createBackgroundCollector(@NotNull SentryOptions options) {
    return null;
  }
}
