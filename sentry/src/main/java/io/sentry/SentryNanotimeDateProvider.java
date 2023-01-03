package io.sentry;

public final class SentryNanotimeDateProvider implements SentryDateProvider {

  @Override
  public SentryDate now() {
    return new SentryNanotimeDate();
  }
}
