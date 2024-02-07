package io.sentry.android.core;

import androidx.annotation.NonNull;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public class SpanFrameMetricsCollector
    implements IPerformanceContinuousCollector,
        SentryFrameMetricsCollector.FrameMetricsCollectorListener {

  // 30s span duration at 120fps = 3600 frames
  private static final int MAX_FRAMES_COUNT = 3600;
  private static final long ONE_SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);

  private final @NotNull SentryAndroidOptions options;
  private final @NotNull FrameTimeProvider frameTimeProvider;
  private final boolean enabled;

  private final @NotNull Object lock = new Object();
  private final @Nullable SentryFrameMetricsCollector frameMetricsCollector;

  private volatile @Nullable String listenerId;

  // <spanId, start time in nanos>
  private final @NotNull Map<SpanId, Long> runningSpans;
  private final @NotNull LinkedList<Frame> frames;

  // assume 60fps until we get a value reported by the system
  private long lastKnownFrameDurationNanos = 16_666_666L;

  public SpanFrameMetricsCollector(final @NotNull SentryAndroidOptions options) {
    //noinspection Convert2MethodRef
    this(options, () -> System.nanoTime());
  }

  @TestOnly
  public SpanFrameMetricsCollector(
      final @NotNull SentryAndroidOptions options,
      final @NotNull FrameTimeProvider frameTimeProvider) {
    this.options = options;
    this.frameTimeProvider = frameTimeProvider;

    frameMetricsCollector = options.getFrameMetricsCollector();
    enabled = options.isEnablePerformanceV2() && options.isEnableFramesTracking();

    frames = new LinkedList<>();
    runningSpans = new HashMap<>();
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
      final long now = frameTimeProvider.now();
      runningSpans.put(span.getSpanContext().getSpanId(), now);

      if (listenerId == null) {
        if (frameMetricsCollector != null) {
          listenerId = frameMetricsCollector.startCollection(this);
        }
      }

      ensureFrameSizeLimit();
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

    ensureFrameSizeLimit();
    attachFrameMetrics(span);

    // stop collecting in case there are no more running spans
    synchronized (lock) {
      if (runningSpans.isEmpty()) {
        clear();
      }
    }
  }

  private void attachFrameMetrics(@NonNull ISpan span) {
    synchronized (lock) {
      final @Nullable Long spanStartNanos = runningSpans.remove(span.getSpanContext().getSpanId());
      if (spanStartNanos == null) {
        return;
      }

      // ignore spans with no finish date
      final @Nullable SentryDate spanFinishDate = span.getFinishDate();
      if (spanFinishDate == null) {
        return;
      }

      final @NotNull SentryFrameMetrics frameMetrics = new SentryFrameMetrics();

      // project the span end date into a frame time format
      final @NotNull SentryDate now = options.getDateProvider().now();
      final long nowNanos = frameTimeProvider.now();

      final long diffNanos = now.diff(spanFinishDate);
      final long spanEndNanos = nowNanos - diffNanos;

      if (spanStartNanos >= spanEndNanos) {
        return;
      }

      final long spanDurationNanos = spanEndNanos - spanStartNanos;
      long frameDurationNanos = lastKnownFrameDurationNanos;

      if (!frames.isEmpty()) {
        // determine relevant start idx in frames list
        int frameStartIdx = getInsertIdxInFrames(frames, spanStartNanos);
        if (frameStartIdx == -1) {
          frameStartIdx = 0;
        }

        // TODO do not aggregate in case we don't have enough frames covering the total span
        // duration

        // aggregate frames
        for (int frameIdx = frameStartIdx; frameIdx < frames.size(); frameIdx++) {
          final @NotNull Frame frame = frames.get(frameIdx);

          if (frame.startNanos > spanEndNanos) {
            break;
          }

          if (frame.startNanos >= spanStartNanos && frame.endNanos <= spanEndNanos) {
            // if the span contains the frame as a whole, add it 1:1 to the span metrics
            frameMetrics.addFrame(
                frame.durationNanos, frame.delayNanos, frame.isSlow, frame.isFrozen);
          } else if ((spanStartNanos > frame.startNanos && spanStartNanos < frame.endNanos)
              || (spanEndNanos > frame.startNanos && spanEndNanos < frame.endNanos)) {
            // span start or end are within frame
            // calculate the intersection
            final long durationBeforeSpan = Math.max(0, spanStartNanos - frame.startNanos);
            final long delayBeforeSpan =
                Math.max(0, durationBeforeSpan - frame.expectedDurationNanos);
            final long delayWithinSpan =
                Math.min(frame.delayNanos - delayBeforeSpan, spanDurationNanos);

            final long frameStart = Math.max(spanStartNanos, frame.startNanos);
            final long frameEnd = Math.min(spanEndNanos, frame.endNanos);
            final long frameDuration = frameEnd - frameStart;
            frameMetrics.addFrame(
                frameDuration,
                delayWithinSpan,
                SentryFrameMetricsCollector.isSlow(frameDuration, frame.expectedDurationNanos),
                SentryFrameMetricsCollector.isFrozen(frameDuration));
          }

          frameDurationNanos = frame.expectedDurationNanos;
        }
      }

      // if there are no content changes on Android, also no frames are rendered
      // thus no frame metrics are provided
      // in order to match the span duration with the total frame count,
      // we simply interpolate the total number of frames based on the span duration
      // this way the data is more sound and we also match the output of the cocoa SDK
      // TODO right distinguish between a delayed frame or a normal frame when interpolating
      int totalFrameCount = frameMetrics.getTotalFrameCount();
      if (frameMetrics.containsValidData()) {
        final long frameMetricsDurationNanos = frameMetrics.getTotalDurationNanos();
        final long nonRenderedDuration = spanDurationNanos - frameMetricsDurationNanos;
        if (nonRenderedDuration > 0) {
          totalFrameCount += (int) (nonRenderedDuration / frameDurationNanos);
        }
      }

      final long frameDelayNanos =
          frameMetrics.getSlowFrameDelayNanos() + frameMetrics.getFrozenFrameDelayNanos();

      final double frameDelaySeconds = frameDelayNanos / 1e9d;

      span.setData(SpanDataConvention.FRAMES_TOTAL, totalFrameCount);
      span.setData(SpanDataConvention.FRAMES_SLOW, frameMetrics.getSlowFrameCount());
      span.setData(SpanDataConvention.FRAMES_FROZEN, frameMetrics.getFrozenFrameCount());
      span.setData(SpanDataConvention.FRAMES_DELAY, frameDelaySeconds);

      if (span instanceof ITransaction) {
        span.setMeasurement(MeasurementValue.KEY_FRAMES_TOTAL, totalFrameCount);
        span.setMeasurement(MeasurementValue.KEY_FRAMES_SLOW, frameMetrics.getSlowFrameCount());
        span.setMeasurement(MeasurementValue.KEY_FRAMES_FROZEN, frameMetrics.getFrozenFrameCount());
        span.setMeasurement(MeasurementValue.KEY_FRAMES_DELAY, frameDelaySeconds);
      }
    }
  }

  private void ensureFrameSizeLimit() {
    synchronized (lock) {
      while (frames.size() > MAX_FRAMES_COUNT) {
        frames.removeFirst();
      }
    }
  }

  protected int getInsertIdxInFrames(final @NotNull List<Frame> frames, @NonNull Long timeNanos) {
    int leftIdx = 0;
    int rightIdx = frames.size() - 1;

    while (leftIdx <= rightIdx) {
      int midIdx = (leftIdx + rightIdx) / 2;
      final @NotNull Frame midFrame = frames.get(midIdx);

      // time is within frame
      if ((midFrame.startNanos <= timeNanos && midFrame.endNanos >= timeNanos)
          || (midIdx > 0
              && midFrame.startNanos > timeNanos
              && frames.get(midIdx - 1).endNanos < timeNanos)) {
        return midIdx;
      } else if (midFrame.startNanos > timeNanos) {
        // go left
        rightIdx = midIdx - 1;
      } else if (midFrame.endNanos < timeNanos) {
        // go right
        leftIdx = midIdx + 1;
      }
    }
    return -1;
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
      frames.clear();
    }
  }

  @Override
  public void onFrameMetricCollected(
      long frameStartNanos,
      long frameEndNanos,
      long durationNanos,
      long delayNanos,
      boolean isSlow,
      boolean isFrozen,
      float refreshRate) {

    // buffer is full, skip adding new frames
    if (frames.size() > MAX_FRAMES_COUNT) {
      return;
    }

    final long expectedFrameDurationNanos =
        (long) ((double) ONE_SECOND_NANOS / (double) refreshRate);
    lastKnownFrameDurationNanos = expectedFrameDurationNanos;

    frames.add(
        new Frame(
            frameStartNanos,
            frameEndNanos,
            durationNanos,
            delayNanos,
            isSlow,
            isFrozen,
            expectedFrameDurationNanos));
  }

  public interface FrameTimeProvider {
    long now();
  }

  public static class Frame {
    private final long startNanos;
    private final long endNanos;
    private final long durationNanos;
    private final long delayNanos;
    private final boolean isSlow;
    private final boolean isFrozen;
    private final long expectedDurationNanos;

    public Frame(
        final long startNanos,
        final long frameEndNanos,
        final long durationNanos,
        final long delayNanos,
        final boolean isSlow,
        final boolean isFrozen,
        final long expectedFrameDurationNanos) {
      this.startNanos = startNanos;
      this.endNanos = frameEndNanos;
      this.durationNanos = durationNanos;
      this.delayNanos = delayNanos;
      this.isSlow = isSlow;
      this.isFrozen = isFrozen;
      this.expectedDurationNanos = expectedFrameDurationNanos;
    }
  }
}
