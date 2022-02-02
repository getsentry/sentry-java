package io.sentry.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class SentryExecutors {

  private SentryExecutors() {}

  public static final ScheduledExecutorService tracingExecutor = new ScheduledThreadPoolExecutor(1);
}
