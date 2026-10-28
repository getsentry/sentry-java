package io.sentry.internal.eventprocessor;

import io.sentry.EventProcessor;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.util.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EventProcessorAndOrder implements Comparable<EventProcessorAndOrder> {

  private final @NotNull EventProcessor eventProcessor;
  private final long order;

  public EventProcessorAndOrder(
      final @NotNull EventProcessor eventProcessor, final @Nullable Long order) {
    this(eventProcessor, order != null ? order : System.nanoTime());
  }

  private EventProcessorAndOrder(final @NotNull EventProcessor eventProcessor, final long order) {
    this.eventProcessor = eventProcessor;
    this.order = order;
  }

  public @NotNull EventProcessorAndOrder fork(final @NotNull ILogger logger) {
    EventProcessor forkedProcessor = eventProcessor;
    try {
      final @Nullable EventProcessor candidate = eventProcessor.fork();
      if (candidate != null) {
        forkedProcessor = candidate;
      } else {
        logger.log(
            SentryLevel.ERROR,
            "EventProcessor.fork() returned null; reusing the original processor.");
      }
    } catch (Throwable t) {
      ExceptionUtils.rethrowIfFatal(t);
      logger.log(
          SentryLevel.ERROR, "EventProcessor.fork() failed; reusing the original processor.", t);
    }
    return new EventProcessorAndOrder(forkedProcessor, order);
  }

  public @NotNull EventProcessor getEventProcessor() {
    return eventProcessor;
  }

  public @NotNull Long getOrder() {
    return order;
  }

  @Override
  public int compareTo(@NotNull EventProcessorAndOrder o) {
    return Long.compare(order, o.order);
  }
}
