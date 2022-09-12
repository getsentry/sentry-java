package io.sentry.measurement;

import io.sentry.DateUtils;
import io.sentry.SentryTracer;
import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MeasurementContext {

  final @NotNull Date startTimestamp;
  final @Nullable Double endTimestamp;

  public MeasurementContext(@NotNull SentryTracer tracer) {
    startTimestamp = tracer.getStartTimestamp();
    endTimestamp = tracer.getTimestamp();
  }

  public @Nullable Double getDuration() {
    if (endTimestamp != null) {
      return endTimestamp - DateUtils.dateToSeconds(startTimestamp);
    }

    return null;
  }
}
