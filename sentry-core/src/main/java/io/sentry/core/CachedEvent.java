package io.sentry.core;

// Used as a hint. SDK won't cache events captured with this hint
// Transport can mark this instance as resend=true to stop caller from deleting the file.
public final class CachedEvent {
  private boolean resend = false;

  public boolean isResend() {
    return resend;
  }

  public void setResend(boolean resend) {
    this.resend = resend;
  }
}
