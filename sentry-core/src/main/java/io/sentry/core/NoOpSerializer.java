package io.sentry.core;

import java.io.Reader;
import java.io.Writer;

/** No-op implementation of ISerializer */
final class NoOpSerializer implements ISerializer {

  private static final NoOpSerializer instance = new NoOpSerializer();

  public static NoOpSerializer getInstance() {
    return instance;
  }

  private NoOpSerializer() {}

  @Override
  public SentryEvent deserializeEvent(Reader reader) {
    return null;
  }

  @Override
  public void serialize(SentryEvent event, Writer writer) {}
}
