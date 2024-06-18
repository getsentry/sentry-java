package io.sentry;

import org.jetbrains.annotations.Nullable;

public final class NoOpScopesStorage implements IScopesStorage {
  private static final NoOpScopesStorage instance = new NoOpScopesStorage();

  private NoOpScopesStorage() {}

  public static NoOpScopesStorage getInstance() {
    return instance;
  }

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

  // TODO [POTEL] extract into its own class
  public static final class NoOpScopesLifecycleToken implements ISentryLifecycleToken {

    private static final NoOpScopesLifecycleToken instance = new NoOpScopesLifecycleToken();

    private NoOpScopesLifecycleToken() {}

    public static NoOpScopesLifecycleToken getInstance() {
      return instance;
    }

    @Override
    public void close() {}
  }
}
