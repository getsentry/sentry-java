package io.sentry.core.transport;

import io.sentry.core.SentryEvent;
import io.sentry.core.cache.IEventCache;
import java.util.ArrayList;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;

public final class NoOpEventCache implements IEventCache {
  private static final NoOpEventCache instance = new NoOpEventCache();

  public static NoOpEventCache getInstance() {
    return instance;
  }

  private NoOpEventCache() {}

  @Override
  public void store(SentryEvent event) {}

  @Override
  public void discard(SentryEvent event) {}

  @Override
  public @NotNull Iterator<SentryEvent> iterator() {
    return new ArrayList<SentryEvent>(0).iterator();
  }
}
