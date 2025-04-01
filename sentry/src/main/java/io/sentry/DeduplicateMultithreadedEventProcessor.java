package io.sentry;

import static io.sentry.SentryLevel.INFO;

import io.sentry.hints.EventDropReason;
import io.sentry.protocol.SentryException;
import io.sentry.util.HintUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An event processor that deduplicates crash events of the same type that are simultaneously from
 * multiple threads. This can be the case for OutOfMemory errors or CursorWindowAllocationException,
 * basically any error related to allocating memory when it's low.
 */
public final class DeduplicateMultithreadedEventProcessor implements EventProcessor {

  private final @NotNull Map<String, Long> processedEvents =
      Collections.synchronizedMap(new HashMap<>());

  private final @NotNull SentryOptions options;

  public DeduplicateMultithreadedEventProcessor(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @Nullable SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    if (!HintUtils.hasType(hint, UncaughtExceptionHandlerIntegration.UncaughtExceptionHint.class)) {
      // only dedupe crashes coming from our exception handler, because custom errors/crashes might
      // be sent on purpose
      return event;
    }

    final SentryException exception = event.getUnhandledException();
    if (exception == null) {
      return event;
    }

    final String type = exception.getType();
    if (type == null) {
      return event;
    }

    final Long currentEventTid = exception.getThreadId();
    if (currentEventTid == null) {
      return event;
    }

    final Long tid = processedEvents.get(type);
    if (tid != null && !tid.equals(currentEventTid)) {
      if (options.getLogger().isEnabled(INFO)) {
        options
            .getLogger()
            .log(
                SentryLevel.INFO,
                "Event %s has been dropped due to multi-threaded deduplication",
                event.getEventId());
      }
      HintUtils.setEventDropReason(hint, EventDropReason.MULTITHREADED_DEDUPLICATION);
      return null;
    }
    processedEvents.put(type, currentEventTid);
    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 7000L;
  }
}
