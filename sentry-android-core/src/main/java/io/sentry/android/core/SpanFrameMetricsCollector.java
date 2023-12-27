package io.sentry.android.core;

import io.sentry.IPerformanceContinuousCollector;
import io.sentry.ISpan;
import io.sentry.NoOpSpan;
import io.sentry.NoOpTransaction;
import io.sentry.SpanDataConvention;
import io.sentry.SpanId;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class SpanFrameMetricsCollector
    implements IPerformanceContinuousCollector,
        SentryFrameMetricsCollector.FrameMetricsCollectorListener {
  private @NotNull final Object lock = new Object();
  private @Nullable final SentryFrameMetricsCollector frameMetricsCollector;
  private @Nullable volatile String listenerId;
  private @NotNull final Map<SpanId, SentryFrameMetrics> metricsAtSpanStart;

  private @NotNull final SentryFrameMetrics currentFrameMetrics;
  private final boolean enabled;

  public SpanFrameMetricsCollector(final @NotNull SentryAndroidOptions options) {
    frameMetricsCollector = options.getFrameMetricsCollector();
    enabled = options.isEnablePerformanceV2() && options.isEnableFramesTracking();

    metricsAtSpanStart = new HashMap<>();
    currentFrameMetrics = new SentryFrameMetrics();
  }

  @Override
  public void onSpanStarted(final @NotNull ISpan span) {
    if (!enabled) {
      return;
    }
    if (span instanceof NoOpSpan) {
      return;
    }
    if (span instanceof NoOpTransaction) {
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
    if (!enabled) {
      return;
    }
    if (span instanceof NoOpSpan) {
      return;
    }
    if (span instanceof NoOpTransaction) {
      return;
    }

    @Nullable SentryFrameMetrics diff = null;
    synchronized (lock) {
      final @Nullable SentryFrameMetrics metricsAtStart =
          metricsAtSpanStart.remove(span.getSpanContext().getSpanId());
      if (metricsAtStart != null) {
        diff = currentFrameMetrics.diffTo(metricsAtStart);
      }
    }
    if (diff != null && diff.containsValidData()) {
      span.setData(SpanDataConvention.FRAMES_SLOW, diff.getSlowFrameCount());
      span.setData(SpanDataConvention.FRAMES_FROZEN, diff.getFrozenFrameCount());
      span.setData(SpanDataConvention.FRAMES_TOTAL, diff.getTotalFrameCount());
    }

    synchronized (lock) {
      if (metricsAtSpanStart.isEmpty()) {
        clear();
      }
    }
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
      metricsAtSpanStart.clear();
      currentFrameMetrics.clear();
    }
  }

  @Override
  public void onFrameMetricCollected(
      final long frameStartNanos,
      final long frameEndNanos,
      final long durationNanos,
      final long delayNanos,
      final boolean isSlow,
      final boolean isFrozen,
      final float refreshRate) {

    if (isFrozen) {
      currentFrameMetrics.addFrozenFrame(durationNanos);
    } else if (isSlow) {
      currentFrameMetrics.addSlowFrame(durationNanos);
    } else {
      currentFrameMetrics.addNormalFrame(durationNanos);
    }
  }
}
