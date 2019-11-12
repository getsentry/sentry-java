package io.sentry.core.transport;

import io.sentry.core.SentryEvent;
import io.sentry.core.cache.IEventCache;
import java.util.ArrayList;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;

final class NoOpEventCache implements IEventCache {
  private static final NoOpEventCache instance = new NoOpEventCache();

  public static NoOpEventCache getInstance() {
    return instance;
  }

  private NoOpEventCache() {}

  @Override
  public void store(SentryEvent event) {}

  @Override
  public void discard(SentryEvent event) {}

  @NotNull
  @Override
  public Iterator<SentryEvent> iterator() {
    return new ArrayList<SentryEvent>(0).iterator();
  }
}
