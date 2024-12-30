package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryOptions;
import io.sentry.SentryReplayOptions;
import io.sentry.protocol.SdkVersion;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RRWebOptionsEvent extends RRWebEvent implements JsonSerializable, JsonUnknown {
  public static final String EVENT_TAG = "options";

  private @NotNull String tag;
  // keeping this untyped so hybrids can easily set what they want
  private @NotNull Map<String, Object> optionsPayload = new HashMap<>();
  // to support unknown json attributes with nesting, we have to have unknown map for each of the
  // nested object in json: { ..., "data": { ..., "payload": { ... } } }
  private @Nullable Map<String, Object> unknown;
  private @Nullable Map<String, Object> dataUnknown;

  public RRWebOptionsEvent() {
    super(RRWebEventType.Custom);
    tag = EVENT_TAG;
  }

  public RRWebOptionsEvent(final @NotNull SentryOptions options) {
    this();
    final SdkVersion sdkVersion = options.getSdkVersion();
    if (sdkVersion != null) {
      optionsPayload.put("nativeSdkName", sdkVersion.getName());
      optionsPayload.put("nativeSdkVersion", sdkVersion.getVersion());
    }
    final @NotNull SentryReplayOptions replayOptions = options.getExperimental().getSessionReplay();
    optionsPayload.put("errorSampleRate", replayOptions.getOnErrorSampleRate());
    optionsPayload.put("sessionSampleRate", replayOptions.getSessionSampleRate());
    optionsPayload.put(
        "maskAllImages",
        replayOptions.getMaskViewClasses().contains(SentryReplayOptions.IMAGE_VIEW_CLASS_NAME));
    optionsPayload.put(
        "maskAllText",
        replayOptions.getMaskViewClasses().contains(SentryReplayOptions.TEXT_VIEW_CLASS_NAME));
    optionsPayload.put("quality", replayOptions.getQuality().serializedName());
    optionsPayload.put(
        "maskedViewClasses", CollectionUtils.joinToString(replayOptions.getMaskViewClasses(), ","));
    optionsPayload.put(
        "unmaskedViewClasses",
        CollectionUtils.joinToString(replayOptions.getUnmaskViewClasses(), ","));
  }

  @NotNull
  public String getTag() {
    return tag;
  }

  public void setTag(final @NotNull String tag) {
    this.tag = tag;
  }

  public @NotNull Map<String, Object> getOptionsPayload() {
    return optionsPayload;
  }

  public void setOptionsPayload(final @NotNull Map<String, Object> optionsPayload) {
    this.optionsPayload = optionsPayload;
  }

  public @Nullable Map<String, Object> getDataUnknown() {
    return dataUnknown;
  }

  public void setDataUnknown(final @Nullable Map<String, Object> dataUnknown) {
    this.dataUnknown = dataUnknown;
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // region json
  public static final class JsonKeys {
    public static final String DATA = "data";
    public static final String PAYLOAD = "payload";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    new RRWebEvent.Serializer().serialize(this, writer, logger);
    writer.name(JsonKeys.DATA);
    serializeData(writer, logger);
    if (unknown != null) {
      for (final String key : unknown.keySet()) {
        final Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private void serializeData(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(RRWebEvent.JsonKeys.TAG).value(tag);
    writer.name(JsonKeys.PAYLOAD);
    serializePayload(writer, logger);
    if (dataUnknown != null) {
      for (String key : dataUnknown.keySet()) {
        Object value = dataUnknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private void serializePayload(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (optionsPayload != null) {
      for (final String key : optionsPayload.keySet()) {
        final Object value = optionsPayload.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<RRWebOptionsEvent> {

    @Override
    public @NotNull RRWebOptionsEvent deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      @Nullable Map<String, Object> unknown = null;

      final RRWebOptionsEvent event = new RRWebOptionsEvent();
      final RRWebEvent.Deserializer baseEventDeserializer = new RRWebEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.DATA:
            deserializeData(event, reader, logger);
            break;
          default:
            if (!baseEventDeserializer.deserializeValue(event, nextName, reader, logger)) {
              if (unknown == null) {
                unknown = new HashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
            }
            break;
        }
      }

      event.setUnknown(unknown);
      reader.endObject();
      return event;
    }

    private void deserializeData(
        final @NotNull RRWebOptionsEvent event,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      @Nullable Map<String, Object> dataUnknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case RRWebEvent.JsonKeys.TAG:
            final String tag = reader.nextStringOrNull();
            event.tag = tag == null ? "" : tag;
            break;
          case JsonKeys.PAYLOAD:
            deserializePayload(event, reader, logger);
            break;
          default:
            if (dataUnknown == null) {
              dataUnknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, dataUnknown, nextName);
        }
      }
      event.setDataUnknown(dataUnknown);
      reader.endObject();
    }

    @SuppressWarnings("unchecked")
    private void deserializePayload(
        final @NotNull RRWebOptionsEvent event,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      @Nullable Map<String, Object> optionsPayload = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        if (optionsPayload == null) {
          optionsPayload = new HashMap<>();
        }
        reader.nextUnknown(logger, optionsPayload, nextName);
      }
      if (optionsPayload != null) {
        event.setOptionsPayload(optionsPayload);
      }
      reader.endObject();
    }
  }
  // endregion json
}
