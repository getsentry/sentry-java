package io.sentry.spring7.cache;

import io.sentry.IScopes;
import java.util.Collection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Wraps a Spring {@link CacheManager} to return Sentry-instrumented caches. */
@ApiStatus.Internal
public final class SentryCacheManagerWrapper implements CacheManager {

  private final @NotNull CacheManager delegate;
  private final @NotNull IScopes scopes;

  public SentryCacheManagerWrapper(
      final @NotNull CacheManager delegate, final @NotNull IScopes scopes) {
    this.delegate = delegate;
    this.scopes = scopes;
  }

  @Override
  public @Nullable Cache getCache(final @NotNull String name) {
    final Cache cache = delegate.getCache(name);
    if (cache == null || cache instanceof SentryCacheWrapper) {
      return cache;
    }
    return new SentryCacheWrapper(cache, scopes);
  }

  @Override
  public @NotNull Collection<String> getCacheNames() {
    return delegate.getCacheNames();
  }
}
