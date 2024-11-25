package io.sentry.android.core;

import io.sentry.DateUtils;
import io.sentry.IPerformanceContinuousCollector;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.NoOpSpan;
import io.sentry.NoOpTransaction;
import io.sentry.SentryDate;
import io.sentry.SentryNanotimeDate;
import io.sentry.SpanDataConvention;
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector;
import io.sentry.protocol.MeasurementValue;
import java.util.Date;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class SpanFrameMetricsCollector
    implements IPerformanceContinuousCollector,
        SentryFrameMetricsCollector.FrameMetricsCollectorListener {

  // 30s span duration at 120fps = 3600 frames
  // this is just an upper limit for frames.size, ensuring that the buffer does not
  // grow indefinitely in case of a long running span
  private static final int MAX_FRAMES_COUNT = 3600;
  private static final long ONE_SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);
  private static final SentryNanotimeDate EMPTY_NANO_TIME = new SentryNanotimeDate(new Date(0), 0);

  private final boolean enabled;
  private final @NotNull Object lock = new Object();
  private final @NotNull SentryFrameMetricsCollector frameMetricsCollector;

  private volatile @Nullable String listenerId;

  // all running spans, sorted by span start nano time
  private final @NotNull SortedSet<ISpan> runningSpans =
      new TreeSet<>(
          (o1, o2) -> {
            int timeDiff = o1.getStartDate().compareTo(o2.getStartDate());
            if (timeDiff != 0) {
              return timeDiff;
            } else {
              // TreeSet uses compareTo to check for duplicates, so ensure that
              // two non-equal spans with the same start date are not considered equal
              return o1.getSpanContext()
                  .getSpanId()
                  .toString()
                  .compareTo(o2.getSpanContext().getSpanId().toString());
            }
          });

  // all collected frames, sorted by frame end time
  // this is a concurrent set, as the frames are added on the main thread,
  // but span starts/finish may happen on any thread
  // the list only holds Frames, but in order to query for a specific span NanoTimeStamp is used
  private final @NotNull ConcurrentSkipListSet<Frame> frames = new ConcurrentSkipListSet<>();

  // assume 60fps until we get a value reported by the system
  private long lastKnownFrameDurationNanos = 16_666_666L;

  public SpanFrameMetricsCollector(
      final @NotNull SentryAndroidOptions options,
      final @NotNull SentryFrameMetricsCollector frameMetricsCollector) {
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
      runningSpans.add(span);

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
    synchronized (lock) {
      if (!runningSpans.contains(span)) {
        return;
      }
    }

    captureFrameMetrics(span);

    synchronized (lock) {
      if (runningSpans.isEmpty()) {
        clear();
      } else {
        // otherwise only remove old/irrelevant frames
        final @NotNull ISpan oldestSpan = runningSpans.first();
        frames.headSet(new Frame(toNanoTime(oldestSpan.getStartDate()))).clear();
      }
    }
  }

  private void captureFrameMetrics(@NotNull final ISpan span) {
    // TODO lock still required?
    synchronized (lock) {
      boolean removed = runningSpans.remove(span);
      if (!removed) {
        return;
      }

      final @Nullable SentryDate spanFinishDate = span.getFinishDate();
      if (spanFinishDate == null) {
        return;
      }

      final long spanStartNanos = toNanoTime(span.getStartDate());
      final long spanEndNanos = toNanoTime(spanFinishDate);
      final long spanDurationNanos = spanEndNanos - spanStartNanos;
      if (spanDurationNanos <= 0) {
        return;
      }

      final @NotNull SentryFrameMetrics frameMetrics = new SentryFrameMetrics();

      long frameDurationNanos = lastKnownFrameDurationNanos;

      if (!frames.isEmpty()) {
        // determine relevant start in frames list
        final Iterator<Frame> iterator = frames.tailSet(new Frame(spanStartNanos)).iterator();

        //noinspection WhileLoopReplaceableByForEach
        while (iterator.hasNext()) {
          final @NotNull Frame frame = iterator.next();

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

      int totalFrameCount = frameMetrics.getSlowFrozenFrameCount();

      final long nextScheduledFrameNanos = frameMetricsCollector.getLastKnownFrameStartTimeNanos();
      // nextScheduledFrameNanos might be -1 if no frames have been scheduled for drawing yet
      // e.g. can happen during early app start
      if (nextScheduledFrameNanos != -1) {
        totalFrameCount +=
            addPendingFrameDelay(
                frameMetrics, frameDurationNanos, spanEndNanos, nextScheduledFrameNanos);
        totalFrameCount +=
            interpolateFrameCount(frameMetrics, frameDurationNanos, spanDurationNanos);
      }
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

    if (isSlow || isFrozen) {
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
      return (int) Math.ceil((double) nonRenderedDuration / frameDurationNanos);
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

  /**
   * Because {@link SentryNanotimeDate#nanoTimestamp()} only gives you millisecond precision, but
   * diff does ¯\_(ツ)_/¯
   *
   * @param date the input date
   * @return a non-unix timestamp in nano precision, similar to {@link System#nanoTime()}.
   */
  private static long toNanoTime(final @NotNull SentryDate date) {
    // SentryNanotimeDate nanotime is based on System.nanotime(), like EMPTY_NANO_TIME,
    // thus diff will simply return the System.nanotime() value of date
    if (date instanceof SentryNanotimeDate) {
      return date.diff(EMPTY_NANO_TIME);
    }

    // e.g. SentryLongDate is unix time based - upscaled to nanos,
    // we need to project it back to System.nanotime() format
    long nowUnixInNanos = DateUtils.millisToNanos(System.currentTimeMillis());
    long shiftInNanos = nowUnixInNanos - date.nanoTimestamp();
    return System.nanoTime() - shiftInNanos;
  }

  private static class Frame implements Comparable<Frame> {
    private final long startNanos;
    private final long endNanos;
    private final long durationNanos;
    private final long delayNanos;
    private final boolean isSlow;
    private final boolean isFrozen;
    private final long expectedDurationNanos;

    Frame(final long timestampNanos) {
      this(timestampNanos, timestampNanos, 0, 0, false, false, 0);
    }

    Frame(
        final long startNanos,
        final long endNanos,
        final long durationNanos,
        final long delayNanos,
        final boolean isSlow,
        final boolean isFrozen,
        final long expectedFrameDurationNanos) {
      this.startNanos = startNanos;
      this.endNanos = endNanos;
      this.durationNanos = durationNanos;
      this.delayNanos = delayNanos;
      this.isSlow = isSlow;
      this.isFrozen = isFrozen;
      this.expectedDurationNanos = expectedFrameDurationNanos;
    }

    @Override
    public int compareTo(final @NotNull Frame o) {
      return Long.compare(this.endNanos, o.endNanos);
    }
  }
}
