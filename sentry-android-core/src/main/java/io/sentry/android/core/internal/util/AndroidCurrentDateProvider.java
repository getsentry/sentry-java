package io.sentry.android.core.internal.util;

import android.os.SystemClock;
import io.sentry.transport.ICurrentDateProvider;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AndroidCurrentDateProvider implements ICurrentDateProvider {

  private static final ICurrentDateProvider instance = new AndroidCurrentDateProvider();

  public static ICurrentDateProvider getInstance() {
    return instance;
  }

  private AndroidCurrentDateProvider() {}

  @Override
  public long getCurrentTimeMillis() {
    return SystemClock.uptimeMillis();
  }
}
