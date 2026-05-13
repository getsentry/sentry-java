// Adapted from Guava RateLimiter.
// https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/RateLimiter.java
package io.sentry.util;

/**
 * A simple token bucket rate limiter.
 */
public final class TokenBucket {

  private final long maxTokens;
  private final long refillIntervalNanos;
  private long tokens;
  private long lastRefillNanos;

  public TokenBucket(long maxTokens, long refillIntervalNanos) {
    this.maxTokens = maxTokens;
    this.refillIntervalNanos = refillIntervalNanos;
    this.tokens = maxTokens;
    this.lastRefillNanos = System.nanoTime();
  }

  public synchronized boolean tryConsume() {
    refill();
    if (tokens > 0) {
      tokens--;
      return true;
    }
    return false;
  }

  private void refill() {
    long now = System.nanoTime();
    long elapsed = now - lastRefillNanos;
    long newTokens = elapsed / refillIntervalNanos;
    if (newTokens > 0) {
      tokens = Math.min(maxTokens, tokens + newTokens);
      lastRefillNanos = now;
    }
  }
}
