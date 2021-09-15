package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeHeader implements JsonSerializable {
  // Event Id must be set if the envelope holds an event, or an item that is related to the event
  // (e.g: attachments, user feedback)
  private final @Nullable SentryId eventId;

  private final @Nullable SdkVersion sdkVersion;

  public SentryEnvelopeHeader(
      final @Nullable SentryId eventId, final @Nullable SdkVersion sdkVersion) {
    this.eventId = eventId;
    this.sdkVersion = sdkVersion;
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

  // JsonSerializable

  public static final class JsonKeys {
    public static final String EVENT_ID = "event_id";
    public static final String SDK = "sdk";
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
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<SentryEnvelopeHeader> {
    @Override
    public @NotNull SentryEnvelopeHeader deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      SentryId eventId = null;
      SdkVersion sdkVersion = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.EVENT_ID:
            eventId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.SDK:
            sdkVersion = new SdkVersion.Deserializer().deserialize(reader, logger);
            break;
          default:
            break;
        }
      }
      SentryEnvelopeHeader sentryEnvelopeHeader = new SentryEnvelopeHeader(eventId, sdkVersion);
      reader.endObject();
      return sentryEnvelopeHeader;
    }
  }
}
