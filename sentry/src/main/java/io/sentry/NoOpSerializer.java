package io.sentry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** No-op implementation of ISerializer */
final class NoOpSerializer implements ISerializer {

  private static final NoOpSerializer instance = new NoOpSerializer();

  public static NoOpSerializer getInstance() {
    return instance;
  }

  private NoOpSerializer() {}

  @Override public <T, R> @Nullable T deserialize(@NotNull Reader reader, @NotNull Class<T> clazz,
    @Nullable JsonDeserializer<R> elementDeserializer) {
    return null;
  }

  @Override
  public @Nullable SentryEnvelope deserializeEnvelope(@NotNull InputStream inputStream) {
    return null;
  }

  @Override
  public <T> void serialize(@NotNull T entity, @NotNull Writer writer) throws IOException {}

  @Override
  public void serialize(@NotNull SentryEnvelope envelope, @NotNull OutputStream outputStream)
      throws Exception {}

  @Override
  public @NotNull String serialize(@NotNull Map<String, Object> data) throws Exception {
    return "";
  }
}
