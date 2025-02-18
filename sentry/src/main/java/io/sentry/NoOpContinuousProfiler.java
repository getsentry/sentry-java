package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;

public final class NoOpContinuousProfiler implements IContinuousProfiler {

  private static final NoOpContinuousProfiler instance = new NoOpContinuousProfiler();

  private NoOpContinuousProfiler() {}

  public static NoOpContinuousProfiler getInstance() {
    return instance;
  }

  @Override
  public void stop() {}

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public void start(final @NotNull TracesSampler tracesSampler) {}

  @Override
  public void close() {}

  @Override
  public void reevaluateSampling() {}

  @Override
  public @NotNull SentryId getProfilerId() {
    return SentryId.EMPTY_ID;
  }
}
