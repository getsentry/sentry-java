package io.sentry.util;

import io.sentry.ISpan;
import io.sentry.measurement.SentryMeasurements;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MeasurementUtils {

  public static final String MEASUREMENTS_DATA_KEY = "sentry-measurements";

  public static void initMeasurements(final @Nullable ISpan span) {
    if (span == null) {
      return;
    }

    ensureMeasurementsObject(span);

    /** dummy code start */
    modifyMeasurements(
        span,
        (measurements) -> {
          measurements.set("a", 1.0);
        });
    /** dummy code end */
  }

  public static @Nullable SentryMeasurements finishMeasurements(final @Nullable ISpan span) {
    @Nullable final SentryMeasurements measurements = getMeasurements(span);
    clearMeasurements(span);
    return measurements;
  }

  public static void clearMeasurements(final @Nullable ISpan span) {
    if (span != null) {
      // TODO how do we clear it?
      //      span.setData(MEASUREMENTS_DATA_KEY, null);
    }
  }

  private static void ensureMeasurementsObject(final @NotNull ISpan span) {
    @Nullable final Object measurements = span.getData(MEASUREMENTS_DATA_KEY);
    if (measurements == null) {
      span.setData(MEASUREMENTS_DATA_KEY, new SentryMeasurements());
    }
  }

  private static @Nullable SentryMeasurements getMeasurements(final @Nullable ISpan span) {
    if (span == null) {
      return null;
    }

    @Nullable final Object measurementsObject = span.getData(MEASUREMENTS_DATA_KEY);
    if (measurementsObject != null) {
      return (SentryMeasurements) measurementsObject;
    }

    return null;
  }

  public static void modifyMeasurements(
      final @Nullable ISpan span,
      final @NotNull HintUtils.SentryConsumer<SentryMeasurements> callback) {
    @Nullable final SentryMeasurements measurements = getMeasurements(span);
    if (measurements != null) {
      callback.accept(measurements);
    }
  }
}
