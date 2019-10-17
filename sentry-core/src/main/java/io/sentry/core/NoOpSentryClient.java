package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.util.Nullable;

class NoOpSentryClient implements ISentryClient {

  private static final NoOpSentryClient instance = new NoOpSentryClient();

  private NoOpSentryClient() {}

  public static NoOpSentryClient getInstance() {
    return instance;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public SentryId captureEvent(SentryEvent event) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public SentryId captureEvent(SentryEvent event, Scope scope) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public SentryId captureMessage(String message) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public SentryId captureMessage(String message, @Nullable Scope scope) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public SentryId captureException(Throwable throwable, @Nullable Scope scope) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public SentryId captureException(Throwable throwable) {
    return SentryId.EMPTY_ID;
  }

  @Override
  public void close() {}

  @Override
  public void flush(long timeoutMills) {}
}
