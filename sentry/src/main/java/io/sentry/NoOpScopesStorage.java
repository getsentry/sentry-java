package io.sentry;

import org.jetbrains.annotations.Nullable;

public final class NoOpScopesStorage implements IScopesStorage {
  private static final NoOpScopesStorage instance = new NoOpScopesStorage();

  private NoOpScopesStorage() {}

  public static NoOpScopesStorage getInstance() {
    return instance;
  }

  @Override
  public void init() {}

  @Override
  public ISentryLifecycleToken set(@Nullable IScopes scopes) {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public @Nullable IScopes get() {
    return NoOpScopes.getInstance();
  }

  @Override
  public void close() {}
}
