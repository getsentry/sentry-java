package io.sentry;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

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
  public UserFeedback deserializeUserFeedback(Reader reader) {
    return null;
  }

  @Override
  public Session deserializeSession(Reader reader) {
    return null;
  }

  @Override
  public SentryEnvelope deserializeEnvelope(InputStream inputStream) {
    return null;
  }

  @Override
  public void serialize(SentryEvent event, Writer writer) {}

  @Override
  public void serialize(Session session, Writer writer) throws IOException {}

  @Override
  public void serialize(UserFeedback userFeedback, Writer writer) throws IOException {}

  @Override
  public void serialize(SentryEnvelope envelope, Writer outputStream) throws Exception {}

  @Override
  public String serialize(Map<String, Object> data) throws Exception {
    return null;
  }
}
