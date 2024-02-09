package io.sentry.android.core;

import io.sentry.IPerformanceContinuousCollector;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.NoOpSpan;
import io.sentry.NoOpTransaction;
import io.sentry.SentryDate;
import io.sentry.SpanDataConvention;
import io.sentry.SpanId;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.protocol.MeasurementValue;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

  private float lastRefreshRate = 60.0f;

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
      int nonRenderedFrameCount = 0;

      // if there are no content changes on Android, also no frames are rendered
      // thus no frame metrics are provided
      // in order to match the span duration with the total frame count,
      // we simply interpolate the total number of frames based on the span duration
      // this way the data is more sound and we also match the output of the cocoa SDK
      final @Nullable SentryDate spanFinishDate = span.getFinishDate();
      if (spanFinishDate != null) {
        final long spanDurationNanos = spanFinishDate.diff(span.getStartDate());

        final long frameMetricsDurationNanos = diff.getTotalDurationNanos();
        final long nonRenderedDuration = spanDurationNanos - frameMetricsDurationNanos;
        final double refreshRate = lastRefreshRate;

        if (nonRenderedDuration > 0 && refreshRate > 0.0d) {
          // e.g. at 60fps we would have 16.6ms per frame
          final long normalFrameDurationNanos =
              (long) ((double) TimeUnit.SECONDS.toNanos(1) / refreshRate);

          nonRenderedFrameCount = (int) (nonRenderedDuration / normalFrameDurationNanos);
        }
      }

      final int totalFrameCount = diff.getTotalFrameCount() + nonRenderedFrameCount;

      span.setData(SpanDataConvention.FRAMES_TOTAL, totalFrameCount);
      span.setData(SpanDataConvention.FRAMES_SLOW, diff.getSlowFrameCount());
      span.setData(SpanDataConvention.FRAMES_FROZEN, diff.getFrozenFrameCount());

      if (span instanceof ITransaction) {
        span.setMeasurement(MeasurementValue.KEY_FRAMES_TOTAL, totalFrameCount);
        span.setMeasurement(MeasurementValue.KEY_FRAMES_SLOW, diff.getSlowFrameCount());
        span.setMeasurement(MeasurementValue.KEY_FRAMES_FROZEN, diff.getFrozenFrameCount());
      }
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
      currentFrameMetrics.addFrozenFrame(durationNanos, delayNanos);
    } else if (isSlow) {
      currentFrameMetrics.addSlowFrame(durationNanos, delayNanos);
    } else {
      currentFrameMetrics.addNormalFrame(durationNanos);
    }

    lastRefreshRate = refreshRate;
  }
}
