package io.sentry.core.transport;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class CurrentDateProvider implements ICurrentDateProvider {

  private static final ICurrentDateProvider instance = new CurrentDateProvider();

  public static ICurrentDateProvider getInstance() {
    return instance;
  }

  private CurrentDateProvider() {}

  @Override
  public final long getCurrentTimeMillis() {
    return System.currentTimeMillis();
  }
}
