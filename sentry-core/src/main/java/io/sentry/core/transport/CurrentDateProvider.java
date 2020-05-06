package io.sentry.core.transport;

final class CurrentDateProvider implements ICurrentDateProvider {

  @Override
  public final long getCurrentTimeMillis() {
    return System.currentTimeMillis();
  }
}
