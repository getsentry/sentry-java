package io.sentry.samples.spring.boot.jakarta;

import java.util.Collection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class SentryCacheManagerWrapper implements CacheManager {
  private final CacheManager delegate;

  public SentryCacheManagerWrapper(CacheManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public Cache getCache(String name) {
    return new SentryCacheWrapper(delegate.getCache(name));
  }

  @Override
  public Collection<String> getCacheNames() {
    return delegate.getCacheNames();
  }
}
