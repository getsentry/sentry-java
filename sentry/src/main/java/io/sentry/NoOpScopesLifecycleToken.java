package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NoOpScopesLifecycleToken implements ISentryLifecycleToken {

  private static final NoOpScopesLifecycleToken instance = new NoOpScopesLifecycleToken();

  private NoOpScopesLifecycleToken() {}

  public static NoOpScopesLifecycleToken getInstance() {
    return instance;
  }

  @Override
  public void close() {}
}
