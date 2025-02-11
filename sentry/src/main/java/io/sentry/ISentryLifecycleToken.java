package io.sentry;

public interface ISentryLifecycleToken extends AutoCloseable {

  // overridden to not have a checked exception on the method.
  @Override
  void close();
}
