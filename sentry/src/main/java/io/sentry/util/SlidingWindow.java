// Adapted from Metrics-Java SlidingWindowReservoir.
// Copyright 2010-2023 Coda Hale and Yammer, Inc.
// Licensed under the Apache License, Version 2.0.
// https://github.com/dropwizard/metrics/blob/main/metrics-core/src/main/java/com/codahale/metrics/SlidingWindowReservoir.java
package io.sentry.util;

import java.util.concurrent.atomic.AtomicLong;

public final class SlidingWindow<T> {

  private final Object[] measurements;
  private final AtomicLong count = new AtomicLong();

  public SlidingWindow(int size) {
    this.measurements = new Object[size];
  }

  public void update(T value) {
    long c = count.incrementAndGet();
    measurements[(int) ((c - 1) % measurements.length)] = value;
  }

  public int size() {
    long c = count.get();
    if (c > measurements.length) {
      return measurements.length;
    }
    return (int) c;
  }

  @SuppressWarnings("unchecked")
  public T get(int index) {
    return (T) measurements[index % measurements.length];
  }
}
