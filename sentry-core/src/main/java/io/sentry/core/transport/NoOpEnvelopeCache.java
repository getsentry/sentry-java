package io.sentry.core.transport;

import io.sentry.core.SentryEnvelope;
import io.sentry.core.cache.IEnvelopeCache;
import java.util.ArrayList;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpEnvelopeCache implements IEnvelopeCache {
  private static final NoOpEnvelopeCache instance = new NoOpEnvelopeCache();

  public static NoOpEnvelopeCache getInstance() {
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
