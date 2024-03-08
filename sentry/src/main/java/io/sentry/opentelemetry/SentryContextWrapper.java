package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.sentry.Scopes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryContextWrapper implements Context {

  private final @NotNull Context delegate;

  private SentryContextWrapper(final @NotNull Context delegate) {
    this.delegate = delegate;
  }

  @Override
  public <V> V get(ContextKey<V> contextKey) {
    return delegate.get(contextKey);
  }

  @Override
  public <V> Context with(ContextKey<V> contextKey, V v) {
    final @NotNull Context modifiedContext = delegate.with(contextKey, v);

    if (isOpentelemetrySpan(contextKey)) {
      return forkCurrentScope(modifiedContext);
    } else {
      return modifiedContext;
    }
  }

  private <V> boolean isOpentelemetrySpan(ContextKey<V> contextKey) {
    return "opentelemetry-trace-span-key".equals(contextKey.toString());
  }

  private static @NotNull Context forkCurrentScope(Context context) {
    final @Nullable Scopes scopesInContext = context.get(SENTRY_SCOPES_KEY);
    final @Nullable Scopes spanScopes = getCurrentSpanScopesFromGlobalStorage(context);

    if (scopesInContext != null && spanScopes != null) {
      if (scopesInContext.isAncestorOf(spanScopes)) {
        return context.with(
            SENTRY_SCOPES_KEY, spanScopes.forkedCurrentScope("contextwrapper::spanancestor"));
      }
    }

    if (scopesInContext != null) {
      return context.with(
          SENTRY_SCOPES_KEY, scopesInContext.forkedCurrentScope("contextwrapper::scopeincontext"));
    }

    if (spanScopes != null) {
      return context.with(
          SENTRY_SCOPES_KEY, spanScopes.forkedCurrentScope("contextwrapper::spanscope"));
    }

    return context.with(SENTRY_SCOPES_KEY, Scopes.forkedRoots("contextwrapper::fallback"));
  }

  private static @Nullable Scopes getCurrentSpanScopesFromGlobalStorage(
      final @NotNull Context context) {
    @Nullable final Span span = Span.fromContext(context);

    if (span != null) {
      return SentryWeakSpanStorage.getInstance().getScopes(span.getSpanContext());
    }

    return null;
  }

  public static @NotNull SentryContextWrapper wrap(final @NotNull Context context) {
    // we have to fork here because the first time we get to wrap a context it may already have a
    // span and a scope
    return new SentryContextWrapper(forkCurrentScope(context));
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
