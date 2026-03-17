package io.sentry.jcache;

import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a JCache {@link Cache} to create Sentry spans for cache operations.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
@ApiStatus.Experimental
public final class SentryJCacheWrapper<K, V> implements Cache<K, V> {

  private static final String TRACE_ORIGIN = "auto.cache.jcache";
  private static final String OPERATION_ATTRIBUTE = "db.operation.name";

  private final @NotNull Cache<K, V> delegate;
  private final @NotNull IScopes scopes;

  public SentryJCacheWrapper(final @NotNull Cache<K, V> delegate) {
    this(delegate, ScopesAdapter.getInstance());
  }

  public SentryJCacheWrapper(final @NotNull Cache<K, V> delegate, final @NotNull IScopes scopes) {
    this.delegate = delegate;
    this.scopes = scopes;
  }

  // -- read operations --

  @Override
  public V get(final K key) {
    final ISpan span = startSpan("cache.get", key, "get");
    if (span == null) {
      return delegate.get(key);
    }
    try {
      final V result = delegate.get(key);
      span.setData(SpanDataConvention.CACHE_HIT_KEY, result != null);
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public Map<K, V> getAll(final Set<? extends K> keys) {
    final ISpan span = startSpanForKeys("cache.get", keys, "getAll");
    if (span == null) {
      return delegate.getAll(keys);
    }
    try {
      final Map<K, V> result = delegate.getAll(keys);
      span.setData(SpanDataConvention.CACHE_HIT_KEY, !result.isEmpty());
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public boolean containsKey(final K key) {
    return delegate.containsKey(key);
  }

  // -- write operations --

  @Override
  public void put(final K key, final V value) {
    final ISpan span = startSpan("cache.put", key, "put");
    if (span == null) {
      delegate.put(key, value);
      return;
    }
    try {
      delegate.put(key, value);
      span.setStatus(SpanStatus.OK);
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public V getAndPut(final K key, final V value) {
    final ISpan span = startSpan("cache.put", key, "getAndPut");
    if (span == null) {
      return delegate.getAndPut(key, value);
    }
    try {
      final V result = delegate.getAndPut(key, value);
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public void putAll(final Map<? extends K, ? extends V> map) {
    final ISpan span = startSpanForKeys("cache.put", map.keySet(), "putAll");
    if (span == null) {
      delegate.putAll(map);
      return;
    }
    try {
      delegate.putAll(map);
      span.setStatus(SpanStatus.OK);
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  // putIfAbsent is not instrumented — we cannot know ahead of time whether the put
  // will actually happen, and emitting a cache.put span for a no-op would be misleading.
  @Override
  public boolean putIfAbsent(final K key, final V value) {
    return delegate.putIfAbsent(key, value);
  }

  // replace and getAndReplace are not instrumented — like putIfAbsent, they are conditional
  // writes (only happen if the key exists / value matches). Emitting a cache.put span for a
  // potential no-op would be misleading.
  @Override
  public boolean replace(final K key, final V oldValue, final V newValue) {
    return delegate.replace(key, oldValue, newValue);
  }

  @Override
  public boolean replace(final K key, final V value) {
    return delegate.replace(key, value);
  }

  @Override
  public V getAndReplace(final K key, final V value) {
    return delegate.getAndReplace(key, value);
  }

  // -- remove operations --

  @Override
  public boolean remove(final K key) {
    final ISpan span = startSpan("cache.remove", key, "remove");
    if (span == null) {
      return delegate.remove(key);
    }
    try {
      final boolean result = delegate.remove(key);
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public boolean remove(final K key, final V oldValue) {
    final ISpan span = startSpan("cache.remove", key, "remove");
    if (span == null) {
      return delegate.remove(key, oldValue);
    }
    try {
      final boolean result = delegate.remove(key, oldValue);
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public V getAndRemove(final K key) {
    final ISpan span = startSpan("cache.remove", key, "getAndRemove");
    if (span == null) {
      return delegate.getAndRemove(key);
    }
    try {
      final V result = delegate.getAndRemove(key);
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public void removeAll(final Set<? extends K> keys) {
    final ISpan span = startSpanForKeys("cache.remove", keys, "removeAll");
    if (span == null) {
      delegate.removeAll(keys);
      return;
    }
    try {
      delegate.removeAll(keys);
      span.setStatus(SpanStatus.OK);
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public void removeAll() {
    final ISpan span = startSpan("cache.flush", null, "removeAll");
    if (span == null) {
      delegate.removeAll();
      return;
    }
    try {
      delegate.removeAll();
      span.setStatus(SpanStatus.OK);
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  // -- flush operations --

  @Override
  public void clear() {
    final ISpan span = startSpan("cache.flush", null, "clear");
    if (span == null) {
      delegate.clear();
      return;
    }
    try {
      delegate.clear();
      span.setStatus(SpanStatus.OK);
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public void close() {
    delegate.close();
  }

  // -- entry processor operations --

  @Override
  public <T> T invoke(
      final K key, final EntryProcessor<K, V, T> entryProcessor, final Object... arguments)
      throws EntryProcessorException {
    final ISpan span = startSpan("cache.get", key, "invoke");
    if (span == null) {
      return delegate.invoke(key, entryProcessor, arguments);
    }
    try {
      final T result = delegate.invoke(key, entryProcessor, arguments);
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(
      final Set<? extends K> keys,
      final EntryProcessor<K, V, T> entryProcessor,
      final Object... arguments) {
    final ISpan span = startSpanForKeys("cache.get", keys, "invokeAll");
    if (span == null) {
      return delegate.invokeAll(keys, entryProcessor, arguments);
    }
    try {
      final Map<K, EntryProcessorResult<T>> result =
          delegate.invokeAll(keys, entryProcessor, arguments);
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }

  // -- passthrough operations --

  @Override
  public void loadAll(
      final Set<? extends K> keys,
      final boolean replaceExistingValues,
      final CompletionListener completionListener) {
    delegate.loadAll(keys, replaceExistingValues, completionListener);
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public CacheManager getCacheManager() {
    return delegate.getCacheManager();
  }

  @Override
  public <C extends Configuration<K, V>> C getConfiguration(final Class<C> clazz) {
    return delegate.getConfiguration(clazz);
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public <T> T unwrap(final Class<T> clazz) {
    return delegate.unwrap(clazz);
  }

  @Override
  public void registerCacheEntryListener(
      final CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    delegate.registerCacheEntryListener(cacheEntryListenerConfiguration);
  }

  @Override
  public void deregisterCacheEntryListener(
      final CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    delegate.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
  }

  @Override
  public Iterator<Entry<K, V>> iterator() {
    return delegate.iterator();
  }

  // -- span helpers --

  private @Nullable ISpan startSpan(
      final @NotNull String operation,
      final @Nullable Object key,
      final @NotNull String operationName) {
    if (!scopes.getOptions().isEnableCacheTracing()) {
      return null;
    }

    final ISpan activeSpan = scopes.getSpan();
    if (activeSpan == null || activeSpan.isNoOp()) {
      return null;
    }

    final SpanOptions spanOptions = new SpanOptions();
    spanOptions.setOrigin(TRACE_ORIGIN);
    final String keyString = key != null ? String.valueOf(key) : null;
    final ISpan span = activeSpan.startChild(operation, keyString, spanOptions);
    if (span.isNoOp()) {
      return null;
    }
    if (keyString != null) {
      span.setData(SpanDataConvention.CACHE_KEY_KEY, Arrays.asList(keyString));
    }
    span.setData(OPERATION_ATTRIBUTE, operationName);
    return span;
  }

  private @Nullable ISpan startSpanForKeys(
      final @NotNull String operation,
      final @NotNull Set<?> keys,
      final @NotNull String operationName) {
    if (!scopes.getOptions().isEnableCacheTracing()) {
      return null;
    }

    final ISpan activeSpan = scopes.getSpan();
    if (activeSpan == null || activeSpan.isNoOp()) {
      return null;
    }

    final SpanOptions spanOptions = new SpanOptions();
    spanOptions.setOrigin(TRACE_ORIGIN);
    final ISpan span = activeSpan.startChild(operation, delegate.getName(), spanOptions);
    if (span.isNoOp()) {
      return null;
    }
    span.setData(
        SpanDataConvention.CACHE_KEY_KEY,
        keys.stream().map(String::valueOf).collect(Collectors.toList()));
    span.setData(OPERATION_ATTRIBUTE, operationName);
    return span;
  }
}
