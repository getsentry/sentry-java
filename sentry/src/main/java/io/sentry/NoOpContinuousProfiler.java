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
  public void stopProfiler(final @NotNull ProfileLifecycle profileLifecycle) {}

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public void startProfiler(
      final @NotNull ProfileLifecycle profileLifecycle,
      final @NotNull TracesSampler tracesSampler) {}

  @Override
  public void close(final boolean isTerminating) {}

  @Override
  public void reevaluateSampling() {}

  @Override
  public @NotNull SentryId getProfilerId() {
    return SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId getChunkId() {
    return SentryId.EMPTY_ID;
  }
}
