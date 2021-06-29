package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Writer;

import io.sentry.vendor.gson.stream.JsonWriter;

@ApiStatus.Internal
public final class JsonObjectWriter extends JsonWriter {

  private final JsonObjectSerializer jsonObjectSerializer;

  public JsonObjectWriter(Writer out) {
    super(out);
    jsonObjectSerializer = new JsonObjectSerializer();
  }

  /**
   * Encodes a supported object (Null, String, Boolean, Number, Collection, Array, Map,
   * JsonSerializable).
   *
   * @param logger The logger. May not be null.
   * @param object Object to encode. May be null.
   * @return this writer.
   */
  JsonObjectWriter value(@NotNull ILogger logger, @Nullable Object object) throws Exception {
    jsonObjectSerializer.serialize(this, logger, object);
    return this;
  }
}
