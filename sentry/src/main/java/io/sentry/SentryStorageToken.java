package io.sentry;

public interface SentryStorageToken extends AutoCloseable {
  // overriden to not have a checked exception on the method.
  @Override
  void close();
}
