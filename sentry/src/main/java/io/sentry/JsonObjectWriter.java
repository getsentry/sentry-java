package io.sentry;

import io.sentry.vendor.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Writer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonObjectWriter extends JsonWriter {

  private final JsonObjectSerializer jsonObjectSerializer = new JsonObjectSerializer();

  public JsonObjectWriter(Writer out) {
    super(out);
  }

  /**
   * Encodes a supported object (Null, String, Boolean, Number, Collection, Array, Map,
   * JsonSerializable).
   *
   * @param logger The logger. May not be null.
   * @param object Object to encode. May be null.
   * @return this writer.
   */
  public JsonObjectWriter value(@NotNull ILogger logger, @Nullable Object object)
      throws IOException {
    jsonObjectSerializer.serialize(this, logger, object);
    return this;
  }
}
