package io.sentry.android.core;

import android.app.Activity;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpActivityFramesTracker implements IActivityFramesTracker {
  @Override
  public void addActivity(@NotNull Activity activity) {}

  @Override
  public void setMetrics(@NotNull Activity activity, @NotNull SentryId transactionId) {}

  @Override
  public @Nullable Map<String, @NotNull MeasurementValue> takeMetrics(
      @NotNull SentryId transactionId) {
    return null;
  }

  @Override
  public void stop() {}
}
