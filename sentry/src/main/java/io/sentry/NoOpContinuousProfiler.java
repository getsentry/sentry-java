package io.sentry;

import org.jetbrains.annotations.NotNull;

public final class NoOpContinuousProfiler implements IContinuousProfiler {

  private static final NoOpContinuousProfiler instance = new NoOpContinuousProfiler();

  private NoOpContinuousProfiler() {}

  public static NoOpContinuousProfiler getInstance() {
    return instance;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void setHub(@NotNull IHub hub) {}

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public void close() {}
}
