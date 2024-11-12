package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.sentry.IScopes;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryContextWrapper implements Context {

  private final @NotNull Context delegate;

  private SentryContextWrapper(final @NotNull Context delegate) {
    this.delegate = delegate;
  }

  @Override
  public <V> V get(final @NotNull ContextKey<V> contextKey) {
    return delegate.get(contextKey);
  }

  @Override
  public <V> Context with(final @NotNull ContextKey<V> contextKey, V v) {
    final @NotNull Context modifiedContext = delegate.with(contextKey, v);

    if (isOpentelemetrySpan(contextKey)) {
      return forkCurrentScope(modifiedContext);
    } else {
      return modifiedContext;
    }
  }

  private <V> boolean isOpentelemetrySpan(final @NotNull ContextKey<V> contextKey) {
    return "opentelemetry-trace-span-key".equals(contextKey.toString());
  }

  private static @NotNull Context forkCurrentScope(final @NotNull Context context) {
    final @Nullable IOtelSpanWrapper sentrySpan = getCurrentSpanFromGlobalStorage(context);
    final @Nullable IScopes spanScopes = sentrySpan == null ? null : sentrySpan.getScopes();
    final @NotNull IScopes forkedScopes = forkCurrentScopeInternal(context, spanScopes);
    if (sentrySpan != null) {
      forkedScopes.setActiveSpan(sentrySpan);
    }
    return context.with(SENTRY_SCOPES_KEY, forkedScopes);
  }

  private static @NotNull IScopes forkCurrentScopeInternal(
      final @NotNull Context context, final @Nullable IScopes spanScopes) {
    final @Nullable IScopes scopesInContext = context.get(SENTRY_SCOPES_KEY);

    if (scopesInContext != null && spanScopes != null) {
      if (scopesInContext.isAncestorOf(spanScopes)) {
        return spanScopes.forkedCurrentScope("contextwrapper.spanancestor");
      }
    }

    if (scopesInContext != null) {
      return scopesInContext.forkedCurrentScope("contextwrapper.scopeincontext");
    }

    if (spanScopes != null) {
      return spanScopes.forkedCurrentScope("contextwrapper.spanscope");
    }

    return Sentry.forkedRootScopes("contextwrapper.fallback");
  }

  private static @Nullable IOtelSpanWrapper getCurrentSpanFromGlobalStorage(
      final @NotNull Context context) {
    @Nullable final Span span = Span.fromContextOrNull(context);

    if (span != null) {
      final @Nullable IOtelSpanWrapper sentrySpan =
          SentryWeakSpanStorage.getInstance().getSentrySpan(span.getSpanContext());
      return sentrySpan;
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
