package io.sentry;

import org.jetbrains.annotations.NotNull;

public final class NoOpTransactionProfiler implements ITransactionProfiler {

  private static final NoOpTransactionProfiler instance = new NoOpTransactionProfiler();

  private NoOpTransactionProfiler() {}

  public static NoOpTransactionProfiler getInstance() {
    return instance;
  }

  @Override
  public void onTransactionStart(@NotNull ITransaction transaction) {}

  @Override
  public void onTransactionFinish(@NotNull ITransaction transaction) {}
}
