/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.protocol.jfr.convert;

import java.lang.reflect.Array;
import java.util.HashMap;

public final class Index<T> extends HashMap<T, Integer> {
  private static final long serialVersionUID = 1L;
  private final Class<T> cls;

  public Index(Class<T> cls, T empty) {
    this(cls, empty, 256);
  }

  public Index(Class<T> cls, T empty, int initialCapacity) {
    super(initialCapacity);
    this.cls = cls;
    super.put(empty, 0);
  }

  public int index(T key) {
    Integer index = super.get(key);
    if (index != null) {
      return index;
    } else {
      int newIndex = super.size();
      super.put(key, newIndex);
      return newIndex;
    }
  }

  @SuppressWarnings("unchecked")
  public T[] keys() {
    T[] result = (T[]) Array.newInstance(cls, size());
    keys(result);
    return result;
  }

  public void keys(T[] result) {
    for (Entry<T, Integer> entry : entrySet()) {
      result[entry.getValue()] = entry.getKey();
    }
  }
}
