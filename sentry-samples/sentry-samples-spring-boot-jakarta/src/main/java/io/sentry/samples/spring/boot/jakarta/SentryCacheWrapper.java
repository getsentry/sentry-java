package io.sentry.samples.spring.boot.jakarta;

import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.util.StringUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;

public class SentryCacheWrapper implements Cache {
  private final Cache delegate;

  public SentryCacheWrapper(Cache delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public Object getNativeCache() {
    return delegate.getNativeCache();
  }

  @Override
  public ValueWrapper get(Object key) {
    final ISpan parentSpan = Sentry.getSpan();
    if (parentSpan == null) {
      return delegate.get(key);
    } else {
      ISpan childSpan = parentSpan.startChild("cache.get", StringUtils.toString(key));

      // Describe the cache server you are accessing
      childSpan.setData("network.peer.address", "cache.example.com/supercache");
      childSpan.setData("network.peer.port", 9000);

      // Add the key you want to set
      childSpan.setData("cache.key", key instanceof Collection<?> ? key : Arrays.asList(key));
      try {
        ValueWrapper valueWrapper = delegate.get(key);
        childSpan.setData("cache.hit", valueWrapper != null);

        // Set size of the cached value
        childSpan.setData("cache.item_size", 123);

        return valueWrapper;
      } finally {
        childSpan.finish();
      }
    }
  }

  @Override
  public <T> T get(Object key, Class<T> type) {
    return delegate.get(key, type);
  }

  @Override
  public <T> T get(Object key, Callable<T> valueLoader) {
    return delegate.get(key, valueLoader);
  }

  @Override
  public void put(Object key, Object value) {
    final ISpan parentSpan = Sentry.getSpan();
    if (parentSpan == null) {
      delegate.put(key, value);
    } else {
      ISpan childSpan = parentSpan.startChild("cache.put", StringUtils.toString(key));

      // Describe the cache server you are accessing
      childSpan.setData("network.peer.address", "cache.example.com/supercache");
      childSpan.setData("network.peer.port", 9000);

      // Add the key you want to set
      childSpan.setData("cache.key", key instanceof Collection<?> ? key : Arrays.asList(key));
      try {
        // Set size of the cached value
        childSpan.setData("cache.item_size", 123);

        delegate.put(key, value);
      } finally {
        childSpan.finish();
      }
    }
  }

  @Override
  public void evict(Object key) {
    delegate.evict(key);
  }

  @Override
  public void clear() {
    delegate.clear();
  }
}
