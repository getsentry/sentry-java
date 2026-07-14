// Adapted from Example RateLimiter.
// https://github.com/example
package io.sentry.skills.verification;

public final class HeaderPartiallyStripped {

  public synchronized boolean tryAcquire() {
    return true;
  }
}
