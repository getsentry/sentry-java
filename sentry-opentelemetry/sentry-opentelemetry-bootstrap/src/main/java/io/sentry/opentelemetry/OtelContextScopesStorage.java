package io.sentry.opentelemetry;

import static io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.sentry.IScopes;
import io.sentry.IScopesStorage;
import io.sentry.ISentryLifecycleToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@SuppressWarnings("MustBeClosedChecker")
public final class OtelContextScopesStorage implements IScopesStorage {

  @Override
  public void init() {
    /**
     * We're currently overriding the storage mechanism to allow for cleanup of non closed OTel
     * scopes. These happen when using e.g. Sentry static API due to getCurrentScopes() invoking
     * Context.makeCurrent and then ignoring the returned lifecycle token (OTel Scope). After fixing
     * the classloader problem (sentry bootstrap dependency is currently in agent classloader) we
     * can revisit and try again to set the storage instead of overriding it in the wrapper. We
     * should try to use OTels StorageProvider mechanism instead.
     */
    //    ContextStorage.addWrapper((storage) -> new SentryContextStorage(storage));
    ContextStorage.addWrapper(
        (storage) -> new SentryContextStorage(new SentryOtelThreadLocalStorage()));
  }

  @Override
  public @NotNull ISentryLifecycleToken set(@Nullable IScopes scopes) {
    final Context context = Context.current();
    final @NotNull Scope otelScope = context.with(SENTRY_SCOPES_KEY, scopes).makeCurrent();
    return new OtelStorageToken(otelScope);
  }

  @Override
  public @Nullable IScopes get() {
    final Context context = Context.current();
    return context.get(SENTRY_SCOPES_KEY);
  }

  @Override
  public void close() {}
}
