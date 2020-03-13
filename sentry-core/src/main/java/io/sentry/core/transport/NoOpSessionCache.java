package io.sentry.core.transport;

import io.sentry.core.SentryEnvelope;
import io.sentry.core.cache.ISessionCache;
import java.util.ArrayList;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NoOpSessionCache implements ISessionCache {
  private static final NoOpSessionCache instance = new NoOpSessionCache();

  public static NoOpSessionCache getInstance() {
    return instance;
  }

  @Override
  public void store(SentryEnvelope envelope, @Nullable Object hint) {}

  @Override
  public void discard(SentryEnvelope envelope) {}

  @NotNull
  @Override
  public Iterator<SentryEnvelope> iterator() {
    return new ArrayList<SentryEnvelope>(0).iterator();
  }
}
