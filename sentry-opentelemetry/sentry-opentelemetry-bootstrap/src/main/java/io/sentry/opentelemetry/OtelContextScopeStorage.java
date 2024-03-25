package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_HUB_KEY;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.sentry.IHub;
import io.sentry.ScopeStorage;
import io.sentry.SentryStorageToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("MustBeClosedChecker")
public final class OtelContextScopeStorage implements ScopeStorage {

  @Override
  public SentryStorageToken set(@Nullable IHub hub) {
    // TODO use scopes key
    Scope otelScope = Context.current().with(SENTRY_HUB_KEY, hub).makeCurrent();
    return new OtelContextScopeStorageToken(otelScope);
  }

  @Override
  public @Nullable IHub get() {
    return Context.current().get(SENTRY_HUB_KEY);
  }

  @Override
  public void close() {
    // TODO can we do something here?
  }

  static final class OtelContextScopeStorageToken implements SentryStorageToken {

    private final @NotNull Scope otelScope;

    OtelContextScopeStorageToken(final @NotNull Scope otelScope) {
      this.otelScope = otelScope;
    }

    @Override
    public void close() {
      otelScope.close();
    }
  }
}
