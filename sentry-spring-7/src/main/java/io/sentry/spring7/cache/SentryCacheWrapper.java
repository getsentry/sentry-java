package io.sentry.spring7.cache;

import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.SpanDataConvention;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.cache.Cache;

/** Wraps a Spring {@link Cache} to create Sentry spans for cache operations. */
@ApiStatus.Internal
public final class SentryCacheWrapper implements Cache {

  private static final String TRACE_ORIGIN = "auto.cache.spring";

  private final @NotNull Cache delegate;
  private final @NotNull IScopes scopes;

  public SentryCacheWrapper(final @NotNull Cache delegate, final @NotNull IScopes scopes) {
    this.delegate = delegate;
    this.scopes = scopes;
  }

  @Override
  public @NotNull String getName() {
    return delegate.getName();
  }

  @Override
  public @NotNull Object getNativeCache() {
    return delegate.getNativeCache();
  }

  @Override
  public @Nullable ValueWrapper get(final @NotNull Object key) {
    final ISpan span = startSpan("cache.get", key);
    if (span == null) {
      return delegate.get(key);
    }
    try {
      final ValueWrapper result = delegate.get(key);
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
  public @Nullable <T> T get(final @NotNull Object key, final @Nullable Class<T> type) {
    final ISpan span = startSpan("cache.get", key);
    if (span == null) {
      return delegate.get(key, type);
    }
    try {
      final T result = delegate.get(key, type);
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
  public @Nullable <T> T get(final @NotNull Object key, final @NotNull Callable<T> valueLoader) {
    final ISpan span = startSpan("cache.get", key);
    if (span == null) {
      return delegate.get(key, valueLoader);
    }
    try {
      final AtomicBoolean loaderInvoked = new AtomicBoolean(false);
      final T result =
          delegate.get(
              key,
              () -> {
                loaderInvoked.set(true);
                return valueLoader.call();
              });
      span.setData(SpanDataConvention.CACHE_HIT_KEY, !loaderInvoked.get());
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
  public void put(final @NotNull Object key, final @Nullable Object value) {
    final ISpan span = startSpan("cache.put", key);
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

  // putIfAbsent is not instrumented — we cannot know ahead of time whether the put
  // will actually happen, and emitting a cache.put span for a no-op would be misleading.
  // This matches sentry-python and sentry-javascript which also skip conditional puts.
  // We must override to bypass the default implementation which calls this.get() + this.put().
  @Override
  public @Nullable ValueWrapper putIfAbsent(
      final @NotNull Object key, final @Nullable Object value) {
    return delegate.putIfAbsent(key, value);
  }

  @Override
  public void evict(final @NotNull Object key) {
    final ISpan span = startSpan("cache.remove", key);
    if (span == null) {
      delegate.evict(key);
      return;
    }
    try {
      delegate.evict(key);
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
  public boolean evictIfPresent(final @NotNull Object key) {
    final ISpan span = startSpan("cache.remove", key);
    if (span == null) {
      return delegate.evictIfPresent(key);
    }
    try {
      final boolean result = delegate.evictIfPresent(key);
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
  public void clear() {
    final ISpan span = startSpan("cache.flush", null);
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
  public boolean invalidate() {
    final ISpan span = startSpan("cache.flush", null);
    if (span == null) {
      return delegate.invalidate();
    }
    try {
      final boolean result = delegate.invalidate();
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

  private @Nullable ISpan startSpan(final @NotNull String operation, final @Nullable Object key) {
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
    if (keyString != null) {
      span.setData(SpanDataConvention.CACHE_KEY_KEY, Arrays.asList(keyString));
    }
    return span;
  }
}
