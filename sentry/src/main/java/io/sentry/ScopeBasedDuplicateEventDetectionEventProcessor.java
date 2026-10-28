package io.sentry;

import io.sentry.exception.ExceptionMechanismException;
import io.sentry.internal.eventprocessor.SentryEventProcessor;
import io.sentry.time.MonotonicTicker;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ScopeBasedDuplicateEventDetectionEventProcessor implements SentryEventProcessor {
  static final long DEFAULT_CORRELATION_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);
  static final int DEFAULT_MAX_TRACKED_THROWABLES = 1000;

  private final @NotNull SentryOptions options;
  private final @NotNull MonotonicTicker ticker;
  private final long correlationWindowNanos;
  private final int maxTrackedThrowables;
  private final @NotNull Map<Throwable, CapturedThrowable> capturedThrowables =
      Collections.synchronizedMap(new WeakHashMap<>());

  ScopeBasedDuplicateEventDetectionEventProcessor(final @NotNull SentryOptions options) {
    this(
        options,
        options.getMonotonicTicker(),
        DEFAULT_CORRELATION_WINDOW_NANOS,
        DEFAULT_MAX_TRACKED_THROWABLES);
  }

  ScopeBasedDuplicateEventDetectionEventProcessor(
      final @NotNull SentryOptions options,
      final @NotNull MonotonicTicker ticker,
      final long correlationWindowNanos,
      final int maxTrackedThrowables) {
    this.options = options;
    this.ticker = ticker;
    this.correlationWindowNanos = correlationWindowNanos;
    this.maxTrackedThrowables = maxTrackedThrowables;
  }

  @Override
  public @Nullable SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    final @Nullable Throwable throwable = event.getThrowable();
    if (throwable == null) {
      return event;
    }

    final @NotNull CaptureSource source = getCaptureSource(event.getThrowableMechanism());
    final long now = ticker.tickNanos();
    boolean duplicate = false;

    synchronized (capturedThrowables) {
      pruneStaleEntries(now);
      final @Nullable CapturedThrowable previous = capturedThrowables.get(throwable);
      if (previous == null) {
        if (capturedThrowables.size() < maxTrackedThrowables) {
          capturedThrowables.put(throwable, new CapturedThrowable(source, now));
        }
      } else if (previous.source.isSameSource(source)) {
        capturedThrowables.put(throwable, new CapturedThrowable(source, now));
      } else {
        capturedThrowables.remove(throwable);
        duplicate = true;
      }
    }

    if (duplicate) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Duplicate Exception from a different capture source detected. Event %s will be discarded.",
              event.getEventId());
      return null;
    }
    return event;
  }

  @Override
  public @NotNull EventProcessor fork() {
    return new ScopeBasedDuplicateEventDetectionEventProcessor(
        options, ticker, correlationWindowNanos, maxTrackedThrowables);
  }

  private void pruneStaleEntries(final long now) {
    final Iterator<Map.Entry<Throwable, CapturedThrowable>> iterator =
        capturedThrowables.entrySet().iterator();
    while (iterator.hasNext()) {
      final CapturedThrowable captured = iterator.next().getValue();
      if (now - captured.timestampNanos >= correlationWindowNanos) {
        iterator.remove();
      }
    }
  }

  private static @NotNull CaptureSource getCaptureSource(
      final @Nullable Throwable throwableMechanism) {
    if (throwableMechanism instanceof ExceptionMechanismException) {
      final @Nullable String mechanismType =
          ((ExceptionMechanismException) throwableMechanism).getExceptionMechanism().getType();
      if (mechanismType != null && !mechanismType.isEmpty()) {
        return new CaptureSource(mechanismType);
      }
    }
    return CaptureSource.UNKNOWN;
  }

  private static final class CapturedThrowable {
    private final @NotNull CaptureSource source;
    private final long timestampNanos;

    private CapturedThrowable(final @NotNull CaptureSource source, final long timestampNanos) {
      this.source = source;
      this.timestampNanos = timestampNanos;
    }
  }

  private static final class CaptureSource {
    private static final @NotNull CaptureSource UNKNOWN = new CaptureSource(null);

    private final @Nullable String mechanismType;

    private CaptureSource(final @Nullable String mechanismType) {
      this.mechanismType = mechanismType;
    }

    private boolean isSameSource(final @NotNull CaptureSource other) {
      if (mechanismType == null) {
        return other.mechanismType == null;
      }
      return mechanismType.equals(other.mechanismType);
    }
  }
}
