package io.sentry.util;

import static io.sentry.SentryOptions.MAX_EVENT_SIZE_BYTES;

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
 * exceeds the limit.
 */
@ApiStatus.Internal
public final class EventSizeLimitingUtils {

  private static final int MAX_FRAMES_PER_STACK = 500;
  private static final int FRAMES_PER_SIDE = MAX_FRAMES_PER_STACK / 2;

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
    try {
      if (!options.isEnableEventSizeLimiting()) {
        return event;
      }

      if (isSizeOk(event, options)) {
        return event;
      }

      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "Event %s exceeds %d bytes limit. Reducing size by dropping fields.",
              event.getEventId(),
              MAX_EVENT_SIZE_BYTES);

      @NotNull SentryEvent reducedEvent = event;

      final @Nullable SentryOptions.OnOversizedEventCallback callback =
          options.getOnOversizedEvent();
      if (callback != null) {
        try {
          reducedEvent = callback.execute(reducedEvent, hint);
          if (isSizeOk(reducedEvent, options)) {
            return reducedEvent;
          }
        } catch (Throwable e) {
          options
              .getLogger()
              .log(
                  SentryLevel.ERROR,
                  "The onOversizedEvent callback threw an exception. It will be ignored and automatic reduction will continue.",
                  e);
          reducedEvent = event;
        }
      }

      reducedEvent = removeAllBreadcrumbs(reducedEvent, options);
      if (isSizeOk(reducedEvent, options)) {
        return reducedEvent;
      }

      reducedEvent = truncateStackFrames(reducedEvent, options);
      if (!isSizeOk(reducedEvent, options)) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Event %s still exceeds size limit after reducing all fields. Event may be rejected by server.",
                event.getEventId());
      }

      return reducedEvent;
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "An error occurred while limiting event size. Event will be sent as-is.",
              e);
      return event;
    }
  }

  private static boolean isSizeOk(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    final long size =
        JsonSerializationUtils.byteSizeOf(options.getSerializer(), options.getLogger(), event);
    return size <= MAX_EVENT_SIZE_BYTES;
  }

  private static @NotNull SentryEvent removeAllBreadcrumbs(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    final @Nullable List<Breadcrumb> breadcrumbs = event.getBreadcrumbs();
    if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
      event.setBreadcrumbs(null);
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Removed breadcrumbs to reduce size of event %s",
              event.getEventId());
    }
    return event;
  }

  private static @NotNull SentryEvent truncateStackFrames(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    final @Nullable List<SentryException> exceptions = event.getExceptions();
    if (exceptions != null) {
      for (final @NotNull SentryException exception : exceptions) {
        final @Nullable SentryStackTrace stacktrace = exception.getStacktrace();
        if (stacktrace != null) {
          truncateStackFramesInStackTrace(
              stacktrace, event, options, "Truncated exception stack frames of event %s");
        }
      }
    }

    final @Nullable List<SentryThread> threads = event.getThreads();
    if (threads != null) {
      for (final SentryThread thread : threads) {
        final @Nullable SentryStackTrace stacktrace = thread.getStacktrace();
        if (stacktrace != null) {
          truncateStackFramesInStackTrace(
              stacktrace, event, options, "Truncated thread stack frames for event %s");
        }
      }
    }

    return event;
  }

  private static void truncateStackFramesInStackTrace(
      final @NotNull SentryStackTrace stacktrace,
      final @NotNull SentryEvent event,
      final @NotNull SentryOptions options,
      final @NotNull String logMessage) {
    final @Nullable List<SentryStackFrame> frames = stacktrace.getFrames();
    if (frames != null && frames.size() > MAX_FRAMES_PER_STACK) {
      final @NotNull List<SentryStackFrame> truncatedFrames = new ArrayList<>(MAX_FRAMES_PER_STACK);
      truncatedFrames.addAll(frames.subList(0, FRAMES_PER_SIDE));
      truncatedFrames.addAll(frames.subList(frames.size() - FRAMES_PER_SIDE, frames.size()));
      stacktrace.setFrames(truncatedFrames);
      options.getLogger().log(SentryLevel.DEBUG, logMessage, event.getEventId());
    }
  }
}
