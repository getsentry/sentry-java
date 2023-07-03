package io.sentry;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ObjectWriter {
  ObjectWriter beginArray() throws IOException;

  ObjectWriter endArray() throws IOException;

  ObjectWriter beginObject() throws IOException;

  ObjectWriter endObject() throws IOException;

  ObjectWriter name(String name) throws IOException;

  ObjectWriter value(String value) throws IOException;

  ObjectWriter nullValue() throws IOException;

  ObjectWriter value(boolean value) throws IOException;

  ObjectWriter value(Boolean value) throws IOException;

  ObjectWriter value(double value) throws IOException;

  ObjectWriter value(long value) throws IOException;

  ObjectWriter value(Number value) throws IOException;

  ObjectWriter value(@NotNull ILogger logger, @Nullable Object object) throws IOException;
}
