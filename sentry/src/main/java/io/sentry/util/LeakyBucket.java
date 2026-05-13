// Adapted from Resilience4j RateLimiter.
// https://github.com/resilience4j/resilience4j/blob/master/resilience4j-ratelimiter/src/main/java/io/github/resilience4j/ratelimiter/internal/AtomicRateLimiter.java
package io.sentry.util;

public final class LeakyBucket {

  private final long capacityNanos;
  private final long leakIntervalNanos;
  private long availablePermissions;
  private long lastLeakNanos;

  public LeakyBucket(int capacity, long leakIntervalNanos) {
    this.capacityNanos = capacity;
    this.leakIntervalNanos = leakIntervalNanos;
    this.availablePermissions = capacity;
    this.lastLeakNanos = System.nanoTime();
  }

  public synchronized boolean tryAcquire() {
    leak();
    if (availablePermissions > 0) {
      availablePermissions--;
      return true;
    }
    return false;
  }

  private void leak() {
    long now = System.nanoTime();
    long elapsed = now - lastLeakNanos;
    long newPermissions = elapsed / leakIntervalNanos;
    if (newPermissions > 0) {
      availablePermissions = Math.min(capacityNanos, availablePermissions + newPermissions);
      lastLeakNanos = now;
    }
  }
}
