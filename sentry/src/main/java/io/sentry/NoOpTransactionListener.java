package io.sentry;

public final class NoOpTransactionListener implements ITransactionListener {

  private static final NoOpTransactionListener instance = new NoOpTransactionListener();

  private NoOpTransactionListener() {}

  public static NoOpTransactionListener getInstance() {
    return instance;
  }

  @Override
  public void onTransactionStart(ITransaction transaction) {}

  @Override
  public void onTransactionEnd(ITransaction transaction) {}
}
