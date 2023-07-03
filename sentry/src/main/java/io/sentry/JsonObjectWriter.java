package io.sentry;

import io.sentry.vendor.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Writer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonObjectWriter implements ObjectWriter {

  private final JsonWriter jsonWriter;
  private final JsonObjectSerializer jsonObjectSerializer;

  public JsonObjectWriter(Writer out, int maxDepth) {
    jsonWriter = new JsonWriter(out);
    jsonObjectSerializer = new JsonObjectSerializer(maxDepth);
  }

  @Override
  public JsonObjectWriter beginArray() throws IOException {
    jsonWriter.beginArray();
    return this;
  }

  @Override
  public JsonObjectWriter endArray() throws IOException {
    jsonWriter.endArray();
    return this;
  }

  @Override
  public JsonObjectWriter beginObject() throws IOException {
    jsonWriter.beginObject();
    return this;
  }

  @Override
  public JsonObjectWriter endObject() throws IOException {
    jsonWriter.endObject();
    return this;
  }

  @Override
  public JsonObjectWriter name(String name) throws IOException {
    jsonWriter.name(name);
    return this;
  }

  @Override
  public JsonObjectWriter value(String value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter nullValue() throws IOException {
    jsonWriter.nullValue();
    return this;
  }

  @Override
  public JsonObjectWriter value(boolean value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(Boolean value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(double value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(long value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(Number value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  /**
   * Encodes a supported object (Null, String, Boolean, Number, Collection, Array, Map,
   * JsonSerializable).
   *
   * @param logger The logger. May not be null.
   * @param object Object to encode. May be null.
   * @return this writer.
   */
  @Override
  public JsonObjectWriter value(@NotNull ILogger logger, @Nullable Object object)
      throws IOException {
    jsonObjectSerializer.serialize(this, logger, object);
    return this;
  }

  public void setIndent(@NotNull final String indent) {
    jsonWriter.setIndent(indent);
  }
}
