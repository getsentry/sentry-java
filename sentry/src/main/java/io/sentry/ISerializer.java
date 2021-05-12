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
  <T> @Nullable T deserialize(@NotNull Reader reader, @NotNull Class<T> clazz);

  @Nullable
  SentryEnvelope deserializeEnvelope(@NotNull InputStream inputStream);

  <T> void serialize(@NotNull T entity, @NotNull Writer writer) throws IOException;

  void serialize(@NotNull SentryEnvelope envelope, @NotNull OutputStream outputStream)
      throws Exception;

  @NotNull
  String serialize(@NotNull Map<String, Object> data) throws Exception;
}
