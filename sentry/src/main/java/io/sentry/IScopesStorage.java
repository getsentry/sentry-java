package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IScopesStorage {

  @NotNull
  ISentryLifecycleToken set(final @Nullable IScopes scopes);

  @Nullable
  IScopes get();

  void close();
}
