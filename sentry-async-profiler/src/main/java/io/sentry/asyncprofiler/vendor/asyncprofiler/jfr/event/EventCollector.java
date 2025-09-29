/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.event;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface EventCollector {

  void collect(Event e);

  void beforeChunk();

  void afterChunk();

  // Returns true if this collector has remaining data to process
  boolean finish();

  void forEach(Visitor visitor);

  interface Visitor {
    void visit(Event event, long samples, long value);
  }
}
