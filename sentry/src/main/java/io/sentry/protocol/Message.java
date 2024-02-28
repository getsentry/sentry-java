package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// https://docs.sentry.io/development/sdk-dev/event-payloads/message/

/**
 * A log entry message.
 *
 * <p>A log message is similar to the `message` attribute on the event itself but can additionally
 * hold optional parameters.
 *
 * <p>```json { "message": { "message": "My raw message with interpreted strings like %s", "params":
 * ["this"] } } ```
 *
 * <p>```json { "message": { "message": "My raw message with interpreted strings like {foo}",
 * "params": {"foo": "this"} } } ```
 */
public final class Message implements JsonUnknown, JsonSerializable {
  /**
   * The formatted message. If `message` and `params` are given, Sentry will attempt to backfill
   * `formatted` if empty.
   *
   * <p>It must not exceed 8192 characters. Longer messages will be truncated.
   */
  private @Nullable String formatted;
  /**
   * The log message with parameter placeholders.
   *
   * <p>This attribute is primarily used for grouping related events together into issues. Therefore
   * this really should just be a string template, i.e. `Sending %d requests` instead of `Sending
   * 9999 requests`. The latter is much better at home in `formatted`.
   *
   * <p>It must not exceed 8192 characters. Longer messages will be truncated.
   */
  private @Nullable String message;
  /**
   * Parameters to be interpolated into the log message. This can be an array of positional
   * parameters as well as a mapping of named arguments to their values.
   */
  private @Nullable List<String> params;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public @Nullable String getFormatted() {
    return formatted;
  }

  /**
   * Sets a formatted String
   *
   * @param formatted a formatted String
   */
  public void setFormatted(final @Nullable String formatted) {
    this.formatted = formatted;
  }

  public @Nullable String getMessage() {
    return message;
  }

  public void setMessage(final @Nullable String message) {
    this.message = message;
  }

  public @Nullable List<String> getParams() {
    return params;
  }

  public void setParams(final @Nullable List<String> params) {
    this.params = CollectionUtils.newArrayList(params);
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String FORMATTED = "formatted";
    public static final String MESSAGE = "message";
    public static final String PARAMS = "params";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (formatted != null) {
      writer.name(JsonKeys.FORMATTED).value(formatted);
    }
    if (message != null) {
      writer.name(JsonKeys.MESSAGE).value(message);
    }
    if (params != null && !params.isEmpty()) {
      writer.name(JsonKeys.PARAMS).value(logger, params);
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

  public static final class Deserializer implements JsonDeserializer<Message> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Message deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      Message message = new Message();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.FORMATTED:
            message.formatted = reader.nextStringOrNull();
            break;
          case JsonKeys.MESSAGE:
            message.message = reader.nextStringOrNull();
            break;
          case JsonKeys.PARAMS:
            List<String> deserializedParams = (List<String>) reader.nextObjectOrNull();
            if (deserializedParams != null) {
              message.params = deserializedParams;
            }
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      message.setUnknown(unknown);
      reader.endObject();
      return message;
    }
  }
}
