package io.sentry.core;

// Used as a hint. SDK won't cache events captured with this hint
// Transport can mark this instance as resend=true to stop caller from deleting the file.
final class CachedEvent {
  private boolean resend = false;

  boolean isResend() {
    return resend;
  }

  void setResend(boolean resend) {
    this.resend = resend;
  }
}
