package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeHeader implements JsonSerializable, JsonUnknown {
  // Event Id must be set if the envelope holds an event, or an item that is related to the event
  // (e.g: attachments, user feedback)
  private final @Nullable SentryId eventId;

  private final @Nullable SdkVersion sdkVersion;

  private final @Nullable TraceState trace;

  private @Nullable Map<String, Object> unknown;

  public SentryEnvelopeHeader(
      final @Nullable SentryId eventId, final @Nullable SdkVersion sdkVersion) {
    this(eventId, sdkVersion, null);
  }

  public SentryEnvelopeHeader(
      final @Nullable SentryId eventId,
      final @Nullable SdkVersion sdkVersion,
      final @Nullable TraceState trace) {
    this.eventId = eventId;
    this.sdkVersion = sdkVersion;
    this.trace = trace;
  }

  public SentryEnvelopeHeader(final @Nullable SentryId eventId) {
    this(eventId, null);
  }

  public SentryEnvelopeHeader() {
    this(new SentryId());
  }

  public @Nullable SentryId getEventId() {
    return eventId;
  }

  public @Nullable SdkVersion getSdkVersion() {
    return sdkVersion;
  }

  public @Nullable TraceState getTrace() {
    return trace;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String EVENT_ID = "event_id";
    public static final String SDK = "sdk";
    public static final String TRACE = "trace";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (eventId != null) {
      writer.name(JsonKeys.EVENT_ID).value(logger, eventId);
    }
    if (sdkVersion != null) {
      writer.name(JsonKeys.SDK).value(logger, sdkVersion);
    }
    if (trace != null) {
      writer.name(JsonKeys.TRACE).value(logger, trace);
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

  public static final class Deserializer implements JsonDeserializer<SentryEnvelopeHeader> {
    @Override
    public @NotNull SentryEnvelopeHeader deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      SentryId eventId = null;
      SdkVersion sdkVersion = null;
      TraceState trace = null;
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.EVENT_ID:
            eventId = reader.nextOrNull(logger, new SentryId.Deserializer());
            break;
          case JsonKeys.SDK:
            sdkVersion = reader.nextOrNull(logger, new SdkVersion.Deserializer());
            break;
          case JsonKeys.TRACE:
            trace = reader.nextOrNull(logger, new TraceState.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      SentryEnvelopeHeader sentryEnvelopeHeader =
          new SentryEnvelopeHeader(eventId, sdkVersion, trace);
      sentryEnvelopeHeader.setUnknown(unknown);
      reader.endObject();
      return sentryEnvelopeHeader;
    }
  }

  // JsonUnknown

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
