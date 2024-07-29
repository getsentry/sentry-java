package io.sentry;

import io.sentry.vendor.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Writer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonObjectWriter implements ObjectWriter {

  private final @NotNull JsonWriter jsonWriter;
  private final @NotNull JsonObjectSerializer jsonObjectSerializer;

  public JsonObjectWriter(final @NotNull Writer out, final int maxDepth) {
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
  public JsonObjectWriter name(final @NotNull String name) throws IOException {
    jsonWriter.name(name);
    return this;
  }

  @Override
  public JsonObjectWriter value(final @Nullable String value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public ObjectWriter jsonValue(@Nullable String value) throws IOException {
    jsonWriter.jsonValue(value);
    return this;
  }

  @Override
  public JsonObjectWriter nullValue() throws IOException {
    jsonWriter.nullValue();
    return this;
  }

  @Override
  public JsonObjectWriter value(final boolean value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(final @Nullable Boolean value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(final double value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(final long value) throws IOException {
    jsonWriter.value(value);
    return this;
  }

  @Override
  public JsonObjectWriter value(final @Nullable Number value) throws IOException {
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
  public JsonObjectWriter value(final @NotNull ILogger logger, final @Nullable Object object)
      throws IOException {
    jsonObjectSerializer.serialize(this, logger, object);
    return this;
  }

  @Override
  public void setLenient(final boolean lenient) {
    jsonWriter.setLenient(lenient);
  }

  public void setIndent(final @NotNull String indent) {
    jsonWriter.setIndent(indent);
  }
}
