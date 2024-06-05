package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.sentry.IScopes;
import io.sentry.IScopesStorage;
import io.sentry.ISentryLifecycleToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("MustBeClosedChecker")
public final class OtelContextScopesStorage implements IScopesStorage {

  @Override
  public ISentryLifecycleToken set(@Nullable IScopes scopes) {
    final @NotNull Scope otelScope =
        Context.current().with(SENTRY_SCOPES_KEY, scopes).makeCurrent();
    return new OtelContextScopesStorageToken(otelScope);
  }

  @Override
  public @Nullable IScopes get() {
    return Context.current().get(SENTRY_SCOPES_KEY);
  }

  @Override
  public void close() {}

  static final class OtelContextScopesStorageToken implements ISentryLifecycleToken {

    private final @NotNull Scope otelScope;

    OtelContextScopesStorageToken(final @NotNull Scope otelScope) {
      this.otelScope = otelScope;
    }

    @Override
    public void close() {
      otelScope.close();
    }
  }
}
