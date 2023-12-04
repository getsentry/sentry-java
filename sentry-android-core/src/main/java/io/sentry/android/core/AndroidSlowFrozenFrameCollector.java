package io.sentry.android.core;

import io.sentry.FrameMetrics;
import io.sentry.IPerformanceContinuousCollector;
import io.sentry.ISpan;
import io.sentry.SpanId;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSlowFrozenFrameCollector
    implements IPerformanceContinuousCollector,
        SentryFrameMetricsCollector.FrameMetricsCollectorListener {
  private @NotNull final Object lock = new Object();
  private @Nullable final SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable volatile String listenerId;
  private @NotNull final ConcurrentHashMap<SpanId, FrameMetrics> metricsPerSpan;

  public AndroidSlowFrozenFrameCollector(@NotNull SentryAndroidOptions options) {
    frameMetricsCollector = options.getFrameMetricsCollector();
    metricsPerSpan = new ConcurrentHashMap<>();
  }

  @Override
  public void onSpanStarted(@NotNull ISpan span) {
    metricsPerSpan.put(span.getSpanContext().getSpanId(), new FrameMetrics());

    synchronized (lock) {
      if (listenerId == null) {
        if (frameMetricsCollector != null) {
          listenerId = frameMetricsCollector.startCollection(this);
        }
      }
    }
  }

  @Override
  public void onSpanFinished(@NotNull ISpan span) {
    final FrameMetrics metrics = metricsPerSpan.remove(span.getSpanContext().getSpanId());
    // TODO add data to span
  }

  @Override
  public void clear() {
    synchronized (lock) {
      if (listenerId != null) {
        if (frameMetricsCollector != null) {
          frameMetricsCollector.stopCollection(listenerId);
        }
        listenerId = null;
      }
    }
  }

  @Override
  public void onFrameMetricCollected(
      long frameEndNanos, long durationNanos, long delayNanos, float refreshRate) {
    for (final @NotNull FrameMetrics metrics : metricsPerSpan.values()) {
      // TODO aggregate metrics for every running span
    }
  }
}
