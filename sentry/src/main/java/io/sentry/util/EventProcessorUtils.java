package io.sentry.util;

import io.sentry.EventProcessor;
import io.sentry.internal.eventprocessor.EventProcessorAndOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.Nullable;

public final class EventProcessorUtils {

  public static List<EventProcessor> unwrap(
      final @Nullable List<EventProcessorAndOrder> orderedEventProcessor) {
    final List<EventProcessor> eventProcessors = new ArrayList<>();

    if (orderedEventProcessor != null) {
      for (EventProcessorAndOrder eventProcessorAndOrder : orderedEventProcessor) {
        eventProcessors.add(eventProcessorAndOrder.getEventProcessor());
      }
    }

    return new CopyOnWriteArrayList<>(eventProcessors);
  }
}
