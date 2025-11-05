package io.sentry.util;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class that limits event size to 1MB by incrementally dropping fields when the event
 * exceeds the limit. This runs after beforeSend and right before sending the event.
 *
 * <p>Fields are reduced in order of least importance:
 *
 * <ol>
 *   <li>All breadcrumbs
 *   <li>Exception stack frames (keep 250 frames from start and 250 frames from end, removing
 *       middle)
 * </ol>
 *
 * <p>Note: Extras, tags, threads, request data, debug meta, and contexts are preserved.
 */
@ApiStatus.Internal
public final class EventSizeLimitingUtils {

  private static final long MAX_EVENT_SIZE_BYTES = 1024 * 1024; // 1MB
  private static final int FRAMES_PER_SIDE = 250; // Keep 250 frames from start and 250 from end

  private EventSizeLimitingUtils() {}

  /**
   * Limits the size of an event by incrementally dropping fields when it exceeds the limit.
   *
   * @param event the event to limit
   * @param hint the hint
   * @param options the SentryOptions
   * @return the potentially reduced event
   */
  public static @Nullable SentryEvent limitEventSize(
      final @NotNull SentryEvent event,
      final @NotNull Hint hint,
      final @NotNull SentryOptions options) {
    if (!options.isEnableEventSizeLimiting()) {
      return event;
    }

    if (!isTooLarge(event, options)) {
      return event;
    }

    long eventSize = byteSizeOf(event, options);
    options
        .getLogger()
        .log(
            SentryLevel.INFO,
            "Event size (%d bytes) exceeds %d bytes limit. Reducing size by dropping fields.",
            eventSize,
            MAX_EVENT_SIZE_BYTES);

    SentryEvent reducedEvent = event;

    // Step 0: Invoke custom callback if defined
    final SentryOptions.OnOversizedErrorCallback onOversizedError = options.getOnOversizedError();
    if (onOversizedError != null) {
      try {
        reducedEvent = onOversizedError.execute(reducedEvent, hint);
        if (!isTooLarge(reducedEvent, options)) {
          return reducedEvent;
        }
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "The onOversizedError callback threw an exception. It will be ignored and automatic reduction will continue.",
                e);
        // Continue with automatic reduction if callback fails
        reducedEvent = event;
      }
    }

    // Step 1: Remove all breadcrumbs
    reducedEvent = removeAllBreadcrumbs(reducedEvent, options);
    if (!isTooLarge(reducedEvent, options)) {
      return reducedEvent;
    }

    // Step 2: Truncate stack frames (keep 250 from start and 250 from end)
    reducedEvent = truncateStackFrames(reducedEvent, options);
    if (isTooLarge(reducedEvent, options)) {
      long finalEventSize = byteSizeOf(reducedEvent, options);
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Event size (%d bytes) still exceeds limit after reducing all fields. Event may be rejected by server.",
              finalEventSize);
    }

    return reducedEvent;
  }

  /**
   * Checks if the event exceeds the size limit.
   *
   * @param event the event to check
   * @param options the SentryOptions
   * @return true if the event exceeds the size limit
   */
  private static boolean isTooLarge(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    return byteSizeOf(event, options) > MAX_EVENT_SIZE_BYTES;
  }

  /** Calculates the size of the event when serialized to JSON without actually storing the data. */
  private static long byteSizeOf(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    return JsonSerializationUtils.byteSizeOf(options.getSerializer(), options.getLogger(), event);
  }

  private static @NotNull SentryEvent removeAllBreadcrumbs(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    final List<Breadcrumb> breadcrumbs = event.getBreadcrumbs();
    if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
      event.setBreadcrumbs(null);
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG, "Removed %d breadcrumbs to reduce event size", breadcrumbs.size());
    }
    return event;
  }

  private static @NotNull SentryEvent truncateStackFrames(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    final List<SentryException> exceptions = event.getExceptions();
    if (exceptions != null) {
      for (final SentryException exception : exceptions) {
        final SentryStackTrace stacktrace = exception.getStacktrace();
        if (stacktrace != null) {
          final List<SentryStackFrame> frames = stacktrace.getFrames();
          if (frames != null && frames.size() > FRAMES_PER_SIDE * 2) {
            // Keep first 250 frames and last 250 frames, removing middle
            final List<SentryStackFrame> truncatedFrames = new ArrayList<>();
            truncatedFrames.addAll(frames.subList(0, FRAMES_PER_SIDE));
            truncatedFrames.addAll(frames.subList(frames.size() - FRAMES_PER_SIDE, frames.size()));
            stacktrace.setFrames(truncatedFrames);
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Truncated stack frames from %d to %d (removed middle) for exception %s",
                    frames.size(),
                    truncatedFrames.size(),
                    exception.getType());
          }
        }
      }
    }

    // Also truncate thread stack traces
    final List<SentryThread> threads = event.getThreads();
    if (threads != null) {
      for (final SentryThread thread : threads) {
        final SentryStackTrace stacktrace = thread.getStacktrace();
        if (stacktrace != null) {
          final List<SentryStackFrame> frames = stacktrace.getFrames();
          if (frames != null && frames.size() > FRAMES_PER_SIDE * 2) {
            // Keep first 250 frames and last 250 frames, removing middle
            final List<SentryStackFrame> truncatedFrames = new ArrayList<>();
            truncatedFrames.addAll(frames.subList(0, FRAMES_PER_SIDE));
            truncatedFrames.addAll(frames.subList(frames.size() - FRAMES_PER_SIDE, frames.size()));
            stacktrace.setFrames(truncatedFrames);
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Truncated stack frames from %d to %d (removed middle) for thread %d",
                    frames.size(),
                    truncatedFrames.size(),
                    thread.getId());
          }
        }
      }
    }

    return event;
  }
}
