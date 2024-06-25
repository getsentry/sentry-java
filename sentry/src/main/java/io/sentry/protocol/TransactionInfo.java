package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TransactionInfo implements JsonSerializable, JsonUnknown {

  private final @Nullable String source;
  private @Nullable Map<String, Object> unknown;

  public TransactionInfo(final @Nullable String source) {
    this.source = source;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String SOURCE = "source";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (source != null) {
      writer.name(JsonKeys.SOURCE).value(logger, source);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<TransactionInfo> {

    @Override
    public @NotNull TransactionInfo deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      String source = null;
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SOURCE:
            source = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }

      TransactionInfo transactionInfo = new TransactionInfo(source);
      transactionInfo.setUnknown(unknown);
      reader.endObject();
      return transactionInfo;
    }
  }
}
