package io.sentry;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ObjectWriter {
  ObjectWriter beginArray() throws IOException;

  ObjectWriter endArray() throws IOException;

  ObjectWriter beginObject() throws IOException;

  ObjectWriter endObject() throws IOException;

  ObjectWriter name(final @NotNull String name) throws IOException;

  ObjectWriter value(final @Nullable String value) throws IOException;

  ObjectWriter jsonValue(final @Nullable String value) throws IOException;

  ObjectWriter nullValue() throws IOException;

  ObjectWriter value(final boolean value) throws IOException;

  ObjectWriter value(final @Nullable Boolean value) throws IOException;

  ObjectWriter value(final double value) throws IOException;

  ObjectWriter value(final long value) throws IOException;

  ObjectWriter value(final @Nullable Number value) throws IOException;

  ObjectWriter value(final @NotNull ILogger logger, final @Nullable Object object)
      throws IOException;

  void setLenient(boolean lenient);

  void setIndent(final @Nullable String indent);

  @Nullable
  String getIndent();
}
