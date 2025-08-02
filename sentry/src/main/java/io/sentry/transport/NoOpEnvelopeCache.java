package io.sentry.transport;

import io.sentry.Hint;
import io.sentry.SentryEnvelope;
import io.sentry.cache.IEnvelopeCache;
import java.util.Collections;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;

public final class NoOpEnvelopeCache implements IEnvelopeCache {
  private static final NoOpEnvelopeCache instance = new NoOpEnvelopeCache();

  public static NoOpEnvelopeCache getInstance() {
    return instance;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void store(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {}

  @Override
  public boolean storeEnvelope(@NotNull SentryEnvelope envelope, @NotNull Hint hint) {
    return false;
  }

  @Override
  public void discard(@NotNull SentryEnvelope envelope) {}

  @NotNull
  @Override
  public Iterator<SentryEnvelope> iterator() {
    return Collections.emptyIterator();
  }
}
