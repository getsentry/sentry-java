package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpTransactionListener implements ITransactionListener {

  private static final NoOpTransactionListener instance = new NoOpTransactionListener();

  private NoOpTransactionListener() {}

  public static NoOpTransactionListener getInstance() {
    return instance;
  }

  @Override
  public void onTransactionStart(@NotNull ITransaction transaction) {}

  @Override
  public @Nullable ProfilingTraceData onTransactionFinish(@NotNull ITransaction transaction) {
    return null;
  }
}
