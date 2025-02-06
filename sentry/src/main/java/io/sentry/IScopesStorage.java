package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IScopesStorage {

  void init();

  @NotNull
  ISentryLifecycleToken set(final @Nullable IScopes scopes);

  @Nullable
  IScopes get();

  void close();
}
