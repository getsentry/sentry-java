package io.sentry.android.core;

import io.sentry.IPerformanceContinuousCollector;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.NoOpSpan;
import io.sentry.NoOpTransaction;
import io.sentry.SentryDate;
import io.sentry.SpanDataConvention;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.protocol.MeasurementValue;
import io.sentry.util.Objects;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
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
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;

  private volatile @Nullable String listenerId;

  // a map of <span-id, span-start-time-nanos>
  // as spans do not contain a nano start time, it's being tracked here
  private final @NotNull Map<String, NanoTimeStamp> spanStarts = new HashMap<>();

  // all running spans, sorted by span start nano time
  private final @NotNull SortedSet<SpanStart> runningSpans = new TreeSet<>();

  // all collected frames, sorted by frame end time
  // this is a concurrent set, as the frames are added on the main thread,
  // but span starts/finish may happen on any thread
  // the list only holds Frames, but in order to query for a specific span NanoTimeStamp is used
  private final @NotNull ConcurrentSkipListSet<NanoTimeStamp> frames =
      new ConcurrentSkipListSet<>();

  // assume 60fps until we get a value reported by the system
  private long lastKnownFrameDurationNanos = 16_666_666L;

  public SpanFrameMetricsCollector(
      final @NotNull SentryAndroidOptions options,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector) {
    //noinspection Convert2MethodRef
    this(options, () -> System.nanoTime(), frameMetricsCollector);
  }

  @TestOnly
  public SpanFrameMetricsCollector(
      final @NotNull SentryAndroidOptions options,
      final @NotNull FrameTimeProvider frameTimeProvider,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector) {
    this.options = options;
    this.frameTimeProvider = frameTimeProvider;
    this.frameMetricsCollector = frameMetricsCollector;

    enabled = options.isEnablePerformanceV2() && options.isEnableFramesTracking();
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
      final NanoTimeStamp timeStamp = new NanoTimeStamp(now);

      final String id = span.getSpanContext().getSpanId().toString();
      runningSpans.add(new SpanStart(id, timeStamp));
      spanStarts.put(id, timeStamp);

      if (listenerId == null) {
        listenerId = frameMetricsCollector.startCollection(this);
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

    // ignore span if onSpanStarted was never called for it
    final @NotNull String spanId = span.getSpanContext().getSpanId().toString();
    synchronized (lock) {
      if (!spanStarts.containsKey(spanId)) {
        return;
      }
    }

    captureFrameMetrics(span);

    synchronized (lock) {
      if (runningSpans.isEmpty()) {
        clear();
      } else {
        // otherwise only remove old/irrelevant frames
        final SpanStart oldestSpan = runningSpans.first();
        frames.headSet(oldestSpan.timeNanos).clear();
      }
    }
  }

  private void captureFrameMetrics(@NotNull final ISpan span) {
    // TODO lock still required?
    synchronized (lock) {
      final @NotNull String id = span.getSpanContext().getSpanId().toString();
      final @Nullable NanoTimeStamp spanStartTimeStamp = spanStarts.remove(id);
      if (spanStartTimeStamp == null) {
        return;
      }
      runningSpans.remove(new SpanStart(id, spanStartTimeStamp));

      // ignore spans with no finish date
      final @Nullable SentryDate spanFinishDate = span.getFinishDate();
      if (spanFinishDate == null) {
        return;
      }

      final @NotNull SentryFrameMetrics frameMetrics = new SentryFrameMetrics();

      // project the span end date into our nano time format
      final @NotNull SentryDate now = options.getDateProvider().now();
      final long nowNanos = frameTimeProvider.now();

      final long diffNanos = now.diff(spanFinishDate);
      final long spanEndNanos = nowNanos - diffNanos;

      final long spanStartNanos = spanStartTimeStamp.timeNanos;
      if (spanStartNanos >= spanEndNanos) {
        return;
      }

      final long spanDurationNanos = spanEndNanos - spanStartNanos;
      long frameDurationNanos = lastKnownFrameDurationNanos;

      if (!frames.isEmpty()) {
        // determine relevant start in frames list
        final Iterator<NanoTimeStamp> iterator =
            frames.tailSet(new NanoTimeStamp(spanStartNanos)).iterator();
        while (iterator.hasNext()) {
          final @NotNull Frame frame = (Frame) iterator.next();

          if (frame.startNanos > spanEndNanos) {
            break;
          }

          if (frame.startNanos >= spanStartNanos && frame.endNanos <= spanEndNanos) {
            // if the frame is contained within the span, add it 1:1 to the span metrics
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

      int totalFrameCount = frameMetrics.getTotalFrameCount();

      final long nextScheduledFrameNanos = frameMetricsCollector.getLastKnownFrameStartTimeNanos();
      totalFrameCount +=
          addPendingFrameDelay(
              frameMetrics, frameDurationNanos, spanEndNanos, nextScheduledFrameNanos);
      totalFrameCount += interpolateFrameCount(frameMetrics, frameDurationNanos, spanDurationNanos);

      final long frameDelayNanos =
          frameMetrics.getSlowFrameDelayNanos() + frameMetrics.getFrozenFrameDelayNanos();
      final double frameDelayInSeconds = frameDelayNanos / 1e9d;

      span.setData(SpanDataConvention.FRAMES_TOTAL, totalFrameCount);
      span.setData(SpanDataConvention.FRAMES_SLOW, frameMetrics.getSlowFrameCount());
      span.setData(SpanDataConvention.FRAMES_FROZEN, frameMetrics.getFrozenFrameCount());
      span.setData(SpanDataConvention.FRAMES_DELAY, frameDelayInSeconds);

      if (span instanceof ITransaction) {
        span.setMeasurement(MeasurementValue.KEY_FRAMES_TOTAL, totalFrameCount);
        span.setMeasurement(MeasurementValue.KEY_FRAMES_SLOW, frameMetrics.getSlowFrameCount());
        span.setMeasurement(MeasurementValue.KEY_FRAMES_FROZEN, frameMetrics.getFrozenFrameCount());
        span.setMeasurement(MeasurementValue.KEY_FRAMES_DELAY, frameDelayInSeconds);
      }
    }
  }

  @Override
  public void clear() {
    synchronized (lock) {
      if (listenerId != null) {
        frameMetricsCollector.stopCollection(listenerId);
        listenerId = null;
      }
      frames.clear();
      runningSpans.clear();
      spanStarts.clear();
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

    // buffer is full, skip adding new frames for now
    // once a span finishes, the buffer will trimmed
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

  private static int interpolateFrameCount(
      final @NotNull SentryFrameMetrics frameMetrics,
      final long frameDurationNanos,
      final long spanDurationNanos) {
    // if there are no content changes on Android, also no new frame metrics are provided by the
    // system
    // in order to match the span duration with the total frame count,
    // we simply interpolate the total number of frames based on the span duration
    // this way the data is more sound and we also match the output of the cocoa SDK
    final long frameMetricsDurationNanos = frameMetrics.getTotalDurationNanos();
    final long nonRenderedDuration = spanDurationNanos - frameMetricsDurationNanos;
    if (nonRenderedDuration > 0) {
      return (int) (nonRenderedDuration / frameDurationNanos);
    }
    return 0;
  }

  private static int addPendingFrameDelay(
      @NotNull final SentryFrameMetrics frameMetrics,
      final long frameDurationNanos,
      final long spanEndNanos,
      final long nextScheduledFrameNanos) {
    final long pendingDurationNanos = Math.max(0, spanEndNanos - nextScheduledFrameNanos);
    final boolean isSlow =
        SentryFrameMetricsCollector.isSlow(pendingDurationNanos, frameDurationNanos);
    if (isSlow) {
      // add a single slow/frozen frame
      final boolean isFrozen = SentryFrameMetricsCollector.isFrozen(pendingDurationNanos);
      final long pendingDelayNanos = Math.max(0, pendingDurationNanos - frameDurationNanos);
      frameMetrics.addFrame(pendingDurationNanos, pendingDelayNanos, true, isFrozen);
      return 1;
    }
    return 0;
  }

  @ApiStatus.Internal
  public interface FrameTimeProvider {
    long now();
  }

  @ApiStatus.Internal
  public static class NanoTimeStamp implements Comparable<NanoTimeStamp> {

    private final long timeNanos;

    public NanoTimeStamp(long timeNanos) {
      this.timeNanos = timeNanos;
    }

    @Override
    public int compareTo(NanoTimeStamp o) {
      return Long.compare(timeNanos, o.timeNanos);
    }
  }

  @ApiStatus.Internal
  public static class SpanStart implements Comparable<SpanStart> {

    private final @NotNull String spanId;
    private final @NotNull NanoTimeStamp timeNanos;

    public SpanStart(@NotNull String spanId, final @NotNull NanoTimeStamp timeNanos) {
      this.spanId = spanId;
      this.timeNanos = timeNanos;
    }

    @Override
    public int compareTo(SpanStart o) {
      final int timeCompare = timeNanos.compareTo(o.timeNanos);
      if (timeCompare == 0) {
        return spanId.compareTo(o.spanId);
      }
      return timeCompare;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final SpanStart spanStart = (SpanStart) o;
      return timeNanos == spanStart.timeNanos && Objects.equals(spanId, spanStart.spanId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(spanId, timeNanos);
    }
  }

  @ApiStatus.Internal
  public static class Frame extends NanoTimeStamp {
    private final long startNanos;
    private final long endNanos;
    private final long durationNanos;
    private final long delayNanos;
    private final boolean isSlow;
    private final boolean isFrozen;
    private final long expectedDurationNanos;

    public Frame(
        final long startNanos,
        final long endNanos,
        final long durationNanos,
        final long delayNanos,
        final boolean isSlow,
        final boolean isFrozen,
        final long expectedFrameDurationNanos) {
      super(endNanos);
      this.startNanos = startNanos;
      this.endNanos = endNanos;
      this.durationNanos = durationNanos;
      this.delayNanos = delayNanos;
      this.isSlow = isSlow;
      this.isFrozen = isFrozen;
      this.expectedDurationNanos = expectedFrameDurationNanos;
    }
  }
}
