package io.sentry;

public final class SentryInstantDateProvider implements SentryDateProvider {
  @Override
  public SentryDate now() {
    return new SentryInstantDate();
  }
}
