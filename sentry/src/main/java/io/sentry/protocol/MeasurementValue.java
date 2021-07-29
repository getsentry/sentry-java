package io.sentry.protocol;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class MeasurementValue {
  @SuppressWarnings("UnusedVariable")
  private final float value;

  public MeasurementValue(final float value) {
    this.value = value;
  }

  @TestOnly
  public float getValue() {
    return value;
  }
}
