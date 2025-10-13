/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.event;

import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.JfrReader;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class GCHeapSummary extends Event {
  public final int gcId;
  public final boolean afterGC;
  public final long committed;
  public final long reserved;
  public final long used;

  public GCHeapSummary(JfrReader jfr) {
    super(jfr.getVarlong(), 0, 0);
    this.gcId = jfr.getVarint();
    this.afterGC = jfr.getVarint() > 0;
    jfr.getVarlong(); // long start
    jfr.getVarlong(); // long committedEnd
    this.committed = jfr.getVarlong();
    jfr.getVarlong(); // long reservedEnd
    this.reserved = jfr.getVarlong();
    this.used = jfr.getVarlong();
  }
}
