/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class MallocLeakAggregator implements EventCollector {
  private final EventCollector wrapped;
  private final Map<Long, MallocEvent> addresses;
  private @NotNull List<MallocEvent> events;

  public MallocLeakAggregator(@NotNull EventCollector wrapped) {
    this.wrapped = wrapped;
    this.addresses = new HashMap<>();
    this.events = new ArrayList<>();
  }

  @Override
  public void collect(Event e) {
    events.add((MallocEvent) e);
  }

  @Override
  public void beforeChunk() {
    events = new ArrayList<>();
  }

  @Override
  public void afterChunk() {
    events.sort(null);

    for (MallocEvent e : events) {
      if (e.size > 0) {
        addresses.put(e.address, e);
      } else {
        addresses.remove(e.address);
      }
    }

    events = new ArrayList<>();
  }

  @Override
  public boolean finish() {
    wrapped.beforeChunk();
    for (Event e : addresses.values()) {
      wrapped.collect(e);
    }
    wrapped.afterChunk();

    // Free memory before the final conversion
    addresses.clear();
    return true;
  }

  @Override
  public void forEach(Visitor visitor) {
    wrapped.forEach(visitor);
  }
}
