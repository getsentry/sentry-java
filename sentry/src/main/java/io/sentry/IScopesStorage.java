package io.sentry;

import org.jetbrains.annotations.Nullable;

public interface IScopesStorage {

  ISentryLifecycleToken set(final @Nullable IScopes scopes);

  @Nullable
  IScopes get();

  void close();
}
