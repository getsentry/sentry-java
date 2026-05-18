// Adapted from Caffeine Cache.
// Copyright 2024 Ben Manes.
// Licensed under the Apache License 2.0.
// https://github.com/ben-manes/caffeine/blob/master/caffeine/src/main/java/com/github/benmanes/caffeine/cache/BoundedLocalCache.java
package io.sentry.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** A simple thread-safe LRU cache backed by a synchronized LinkedHashMap. */
public final class ConcurrentLruCache<K, V> {

  private final Map<K, V> map;

  public ConcurrentLruCache(int maxSize) {
    this.map =
        new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
          }
        };
  }

  public synchronized V get(K key) {
    return map.get(key);
  }

  public synchronized void put(K key, V value) {
    map.put(key, value);
  }

  public synchronized int size() {
    return map.size();
  }
}
