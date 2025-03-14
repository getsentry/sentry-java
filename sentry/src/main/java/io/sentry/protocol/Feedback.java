package io.sentry.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;

// Specs can be found at https://develop.sentry.dev/sdk/data-model/envelope-items/#user-feedback

public class Feedback implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "feedback";

  final @NotNull String message;
  final @Nullable String contactEmail;
  final @Nullable String name;
  final @Nullable SentryId associatedEventId;
  final @Nullable SentryId replayId;
  final @Nullable String url;

  private @Nullable Map<String, Object> unknown;

  public Feedback(
      final @NotNull String message,
      final @Nullable String contactEmail,
      final @Nullable String name,
      final @Nullable SentryId associatedEventId,
      final @Nullable SentryId replayId,
      final @Nullable String url) {
    this.message = message;
    this.contactEmail = contactEmail;
    this.name = name;
    this.associatedEventId = associatedEventId;
    this.replayId = replayId;
    this.url = url;
  }

  public Feedback(final @NotNull Feedback feedback) {
    this.message = feedback.message;
    this.contactEmail = feedback.contactEmail;
    this.name = feedback.name;
    this.associatedEventId = feedback.associatedEventId;
    this.replayId = feedback.replayId;
    this.url = feedback.url;
    this.unknown = feedback.unknown;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String MESSAGE = "message";
    public static final String CONTACT_EMAIL = "contact_email";
    public static final String NAME = "name";
    public static final String ASSOCIATED_EVENT_ID = "associated_event_id";
    public static final String REPLAY_ID = "replay_id";
    public static final String URL = "url";
  }

  // JsonUnknown

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
    throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.MESSAGE).value(message);
    if (contactEmail != null) {
      writer.name(JsonKeys.CONTACT_EMAIL).value(contactEmail);
    }
    if (name != null) {
      writer.name(JsonKeys.NAME).value(name);
    }
    if (associatedEventId != null) {
      writer.name(JsonKeys.ASSOCIATED_EVENT_ID);
      associatedEventId.serialize(writer, logger);
    }
    if (replayId != null) {
      writer.name(JsonKeys.REPLAY_ID);
      replayId.serialize(writer, logger);
    }
    if (url != null) {
      writer.name(JsonKeys.URL).value(url);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  // JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<Feedback> {
    @Override
    public @NotNull Feedback deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
      throws Exception {
      @Nullable String message = null;
      @Nullable String contactEmail = null;
      @Nullable String name = null;
      @Nullable SentryId associatedEventId = null;
      @Nullable SentryId replayId = null;
      @Nullable String url = null;
      @Nullable Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.MESSAGE:
            message = reader.nextStringOrNull();
            break;
          case JsonKeys.CONTACT_EMAIL:
            contactEmail = reader.nextStringOrNull();
            break;
          case JsonKeys.NAME:
            name = reader.nextStringOrNull();
            break;
          case JsonKeys.ASSOCIATED_EVENT_ID:
            associatedEventId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.REPLAY_ID:
            replayId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.URL:
            url = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      if (message == null) {
        String errorMessage = "Missing required field \"" + JsonKeys.MESSAGE + "\"";
        Exception exception = new IllegalStateException(errorMessage);
        logger.log(SentryLevel.ERROR, errorMessage, exception);
        throw exception;
      }

      Feedback feedback = new Feedback(message, contactEmail, name, associatedEventId, replayId, url);
      feedback.setUnknown(unknown);
      return feedback;
    }
  }
}
