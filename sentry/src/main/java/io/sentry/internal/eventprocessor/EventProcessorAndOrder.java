package io.sentry.internal.eventprocessor;

import io.sentry.EventProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EventProcessorAndOrder implements Comparable<EventProcessorAndOrder> {

  private final @NotNull EventProcessor eventProcessor;
  private final @NotNull Long order;

  public EventProcessorAndOrder(
      final @NotNull EventProcessor eventProcessor, final @Nullable Long order) {
    this.eventProcessor = eventProcessor;
    if (order == null) {
      this.order = System.nanoTime();
    } else {
      this.order = order;
    }
  }

  public @NotNull EventProcessor getEventProcessor() {
    return eventProcessor;
  }

  public @NotNull Long getOrder() {
    return order;
  }

  @Override
  public int compareTo(@NotNull EventProcessorAndOrder o) {
    return order.compareTo(o.order);
  }
}
