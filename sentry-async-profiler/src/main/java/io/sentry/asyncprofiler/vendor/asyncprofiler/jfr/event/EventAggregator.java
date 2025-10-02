/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.event;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EventAggregator implements EventCollector {
  private static final int INITIAL_CAPACITY = 1024;

  private final boolean threads;
  private final double grain;
  private @NotNull Event[] keys;
  private @NotNull long[] samples;
  private @NotNull long[] values;
  private int size;
  private double fraction;

  public EventAggregator(boolean threads, double grain) {
    this.threads = threads;
    this.grain = grain;
    this.keys = new Event[INITIAL_CAPACITY];
    this.samples = new long[INITIAL_CAPACITY];
    this.values = new long[INITIAL_CAPACITY];

    beforeChunk();
  }

  public int size() {
    return size;
  }

  @Override
  public void collect(Event e) {
    collect(e, e.samples(), e.value());
  }

  public void collect(Event e, long samples, long value) {
    int mask = keys.length - 1;
    int i = hashCode(e) & mask;
    while (keys[i] != null) {
      if (sameGroup(keys[i], e)) {
        this.samples[i] += samples;
        this.values[i] += value;
        return;
      }
      i = (i + 1) & mask;
    }

    this.keys[i] = e;
    this.samples[i] = samples;
    this.values[i] = value;

    if (++size * 2 > keys.length) {
      resize(keys.length * 2);
    }
  }

  @Override
  public void beforeChunk() {
    if (keys == null || size > 0) {
      keys = new Event[INITIAL_CAPACITY];
      samples = new long[INITIAL_CAPACITY];
      values = new long[INITIAL_CAPACITY];
      size = 0;
    }
  }

  @Override
  public void afterChunk() {
    if (grain > 0) {
      coarsen(grain);
    }
  }

  @Override
  public boolean finish() {
    // Don't set to null as it would break nullability contract
    keys = new Event[0];
    samples = new long[0];
    values = new long[0];
    return false;
  }

  @Override
  public void forEach(Visitor visitor) {
    if (size > 0) {
      for (int i = 0; i < keys.length; i++) {
        if (keys[i] != null) {
          visitor.visit(keys[i], samples[i], values[i]);
        }
      }
    }
  }

  public void coarsen(double grain) {
    fraction = 0;

    for (int i = 0; i < keys.length; i++) {
      if (keys[i] != null) {
        long s0 = samples[i];
        long s1 = round(s0 / grain);
        if (s1 == 0) {
          keys[i] = null;
          size--;
        }
        samples[i] = s1;
        values[i] = (long) (values[i] * ((double) s1 / s0));
      }
    }
  }

  private long round(double d) {
    long r = (long) d;
    if ((fraction += d - r) >= 1.0) {
      fraction -= 1.0;
      r++;
    }
    return r;
  }

  private int hashCode(Event e) {
    return e.hashCode() + (threads ? e.tid * 31 : 0);
  }

  private boolean sameGroup(Event e1, Event e2) {
    return e1.stackTraceId == e2.stackTraceId && (!threads || e1.tid == e2.tid) && e1.sameGroup(e2);
  }

  private void resize(int newCapacity) {
    Event[] newKeys = new Event[newCapacity];
    long[] newSamples = new long[newCapacity];
    long[] newValues = new long[newCapacity];
    int mask = newKeys.length - 1;

    for (int i = 0; i < keys.length; i++) {
      if (keys[i] != null) {
        for (int j = hashCode(keys[i]) & mask; ; j = (j + 1) & mask) {
          if (newKeys[j] == null) {
            newKeys[j] = keys[i];
            newSamples[j] = samples[i];
            newValues[j] = values[i];
            break;
          }
        }
      }
    }

    keys = newKeys;
    samples = newSamples;
    values = newValues;
  }
}
