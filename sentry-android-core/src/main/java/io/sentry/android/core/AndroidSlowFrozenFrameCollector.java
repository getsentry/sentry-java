package io.sentry.android.core;

import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.sentry.FrameMetrics;
import io.sentry.IPerformanceContinuousCollector;
import io.sentry.ISpan;
import io.sentry.NoOpSpan;
import io.sentry.SpanDataConvention;
import io.sentry.SpanId;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;

public class AndroidSlowFrozenFrameCollector
    implements IPerformanceContinuousCollector,
        SentryFrameMetricsCollector.FrameMetricsCollectorListener {
  private @NotNull final Object lock = new Object();
  private @Nullable final SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable volatile String listenerId;
  private @NotNull final Map<SpanId, FrameMetrics> metricsAtSpanStart;

  private @NotNull final FrameMetrics currentFrameMetrics;
  final long nanosInSecond = TimeUnit.SECONDS.toNanos(1);
  final long frozenFrameThresholdNanos = TimeUnit.MILLISECONDS.toNanos(700);
  float lastRefreshRate = 0;

  public AndroidSlowFrozenFrameCollector(@NotNull SentryAndroidOptions options) {
    frameMetricsCollector = options.getFrameMetricsCollector();
    metricsAtSpanStart = new HashMap<>();
    currentFrameMetrics = new FrameMetrics();
  }

  @Override
  public void onSpanStarted(final @NotNull ISpan span) {
    if (span instanceof NoOpSpan) {
      return;
    }
    synchronized (lock) {
      metricsAtSpanStart.put(span.getSpanContext().getSpanId(), currentFrameMetrics.duplicate());

      if (listenerId == null) {
        if (frameMetricsCollector != null) {
          listenerId = frameMetricsCollector.startCollection(this);
        }
      }
    }
  }

  @Override
  public void onSpanFinished(final @NotNull ISpan span) {
    if (span instanceof NoOpSpan) {
      return;
    }
    @Nullable FrameMetrics diff = null;
    synchronized (lock) {
      final @Nullable FrameMetrics metricsAtStart = metricsAtSpanStart.remove(span.getSpanContext().getSpanId());
      if (metricsAtStart != null) {
        diff = currentFrameMetrics.diffTo(metricsAtStart);
      }
    }
    if (diff != null) {
      final int totalFrameCount = diff.getTotalFrameCount();
      if (totalFrameCount > 0) {
        span.setData(SpanDataConvention.FRAMES_SLOW, diff.getSlowFrameCount());
        span.setData(SpanDataConvention.FRAMES_FROZEN, diff.getFrozenFrameCount());
        span.setData(SpanDataConvention.FRAMES_TOTAL, diff.getFastFrameCount());
      }
    }
  }

  @Override
  public void clear() {
    Log.d("TAG", "clear");
    synchronized (lock) {
      if (listenerId != null) {
        if (frameMetricsCollector != null) {
          frameMetricsCollector.stopCollection(listenerId);
        }
        listenerId = null;
      }
      metricsAtSpanStart.clear();
      currentFrameMetrics.clear();
    }
  }


  @Override
  public void onFrameMetricCollected(long frameStartNanos, long frameEndNanos, long durationNanos, long delayNanos, boolean isSlow, boolean isFrozen, float refreshRate) {

    lastRefreshRate = (int) (refreshRate * 100) / 100F;

    if (isFrozen) {
      currentFrameMetrics.addFrozenFrame(durationNanos);
    } else if (isSlow) {
      currentFrameMetrics.addSlowFrame(durationNanos);
    } else {
      currentFrameMetrics.addFastFrame(durationNanos);
    }
  }
}
