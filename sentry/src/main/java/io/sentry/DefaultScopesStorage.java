package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefaultScopesStorage implements IScopesStorage {

  private static final @NotNull ThreadLocal<IScopes> currentScopes = new ThreadLocal<>();

  @Override
  public ISentryLifecycleToken set(@Nullable IScopes scopes) {
    final @Nullable IScopes oldScopes = get();
    currentScopes.set(scopes);
    return new DefaultScopesLifecycleToken(oldScopes);
  }

  @Override
  public @Nullable IScopes get() {
    return currentScopes.get();
  }

  @Override
  public void close() {
    // TODO [HSM] prevent further storing? would this cause problems if singleton, closed and
    // re-initialized?
    currentScopes.remove();
  }

  static final class DefaultScopesLifecycleToken implements ISentryLifecycleToken {

    private final @Nullable IScopes oldValue;

    DefaultScopesLifecycleToken(final @Nullable IScopes scopes) {
      this.oldValue = scopes;
    }

    @Override
    public void close() {
      currentScopes.set(oldValue);
    }
  }
}
