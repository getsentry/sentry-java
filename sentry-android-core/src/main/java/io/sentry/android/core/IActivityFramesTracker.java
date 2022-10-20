package io.sentry.android.core;

import android.app.Activity;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IActivityFramesTracker {
  @SuppressWarnings("NullAway")
  void addActivity(@NotNull Activity activity);

  @SuppressWarnings("NullAway")
  void setMetrics(@NotNull Activity activity, @NotNull SentryId transactionId);

  @Nullable
  Map<String, @NotNull MeasurementValue> takeMetrics(@NotNull SentryId transactionId);

  @SuppressWarnings("NullAway")
  void stop();
}
