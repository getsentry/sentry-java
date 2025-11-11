package io.sentry.asyncprofiler.convert;

import java.util.ArrayList;
import java.util.List;
import one.jfr.event.Event;
import one.jfr.event.EventCollector;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NonAggregatingEventCollector implements EventCollector {
  final List<Event> events = new ArrayList<>();

  @Override
  public void collect(Event e) {
    events.add(e);
  }

  @Override
  public void beforeChunk() {
    // No-op
  }

  @Override
  public void afterChunk() {
    // No-op
  }

  @Override
  public boolean finish() {
    return false;
  }

  @Override
  public void forEach(Visitor visitor) {
    for (Event event : events) {
      visitor.visit(event, event.samples(), event.value());
    }
  }
}
