package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Date;
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

  private final @Nullable TraceContext traceContext;

  private @Nullable Date sentAt;

  private @Nullable Map<String, Object> unknown;

  public SentryEnvelopeHeader(
      final @Nullable SentryId eventId, final @Nullable SdkVersion sdkVersion) {
    this(eventId, sdkVersion, null);
  }

  public SentryEnvelopeHeader(
      final @Nullable SentryId eventId,
      final @Nullable SdkVersion sdkVersion,
      final @Nullable TraceContext traceContext) {
    this.eventId = eventId;
    this.sdkVersion = sdkVersion;
    this.traceContext = traceContext;
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

  public @Nullable TraceContext getTraceContext() {
    return traceContext;
  }

  /**
   * Get the timestamp when the event was sent from the SDK as string in RFC 3339 format. Used for
   * clock drift correction of the event timestamp. The time zone must be UTC.
   */
  public @Nullable Date getSentAt() {
    return sentAt;
  }

  /**
   * Set he timestamp when the event was sent from the SDK as string in RFC 3339 format. Used * for
   * clock drift correction of the event timestamp. The time zone must be UTC.
   *
   * @param sentAt The timestamp should be generated as close as possible to the transmission of the
   *     event, so that the delay between sending the envelope and receiving it on the server-side
   *     is minimized.
   */
  public void setSentAt(@Nullable Date sentAt) {
    this.sentAt = sentAt;
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String EVENT_ID = "event_id";
    public static final String SDK = "sdk";
    public static final String TRACE = "trace";
    public static final String SENT_AT = "sent_at";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (eventId != null) {
      writer.name(JsonKeys.EVENT_ID).value(logger, eventId);
    }
    if (sdkVersion != null) {
      writer.name(JsonKeys.SDK).value(logger, sdkVersion);
    }
    if (traceContext != null) {
      writer.name(JsonKeys.TRACE).value(logger, traceContext);
    }
    if (sentAt != null) {
      writer.name(JsonKeys.SENT_AT).value(logger, DateUtils.getTimestamp(sentAt));
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
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      SentryId eventId = null;
      SdkVersion sdkVersion = null;
      TraceContext traceContext = null;
      Date sentAt = null;
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
            traceContext = reader.nextOrNull(logger, new TraceContext.Deserializer());
            break;
          case JsonKeys.SENT_AT:
            sentAt = reader.nextDateOrNull(logger);
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
          new SentryEnvelopeHeader(eventId, sdkVersion, traceContext);
      sentryEnvelopeHeader.setSentAt(sentAt);
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
