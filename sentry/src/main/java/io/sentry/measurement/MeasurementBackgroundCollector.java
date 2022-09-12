package io.sentry.measurement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MeasurementBackgroundCollector {

  // TODO better name for enum and method
  @NotNull
  MeasurementBackgroundServiceType getMeasurementType();

  @Nullable
  Object collect();
}
