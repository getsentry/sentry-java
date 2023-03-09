package io.sentry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ISerializer {

  <T, R> @Nullable T deserialize(
      @NotNull Reader reader,
      @NotNull Class<T> clazz,
      @Nullable JsonDeserializer<R> elementDeserializer);

  default <T> @Nullable T deserialize(@NotNull Reader reader, @NotNull Class<T> clazz) {
    return deserialize(reader, clazz, null);
  }

  @Nullable
  SentryEnvelope deserializeEnvelope(@NotNull InputStream inputStream);

  <T> void serialize(@NotNull T entity, @NotNull Writer writer) throws IOException;

  /**
   * Serializes an envelope
   *
   * @param envelope an envelope
   * @param outputStream which will not be closed automatically
   * @throws Exception an exception
   */
  void serialize(@NotNull SentryEnvelope envelope, @NotNull OutputStream outputStream)
      throws Exception;

  @NotNull
  String serialize(@NotNull Map<String, Object> data) throws Exception;
}
