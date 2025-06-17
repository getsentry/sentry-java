package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Specs can be found at https://develop.sentry.dev/sdk/data-model/envelope-items/#user-feedback

public final class Feedback implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "feedback";

  private @NotNull String message;
  private @Nullable String contactEmail;
  private @Nullable String name;
  private @Nullable SentryId associatedEventId;
  private @Nullable SentryId replayId;
  private @Nullable String url;

  private @Nullable Map<String, Object> unknown;

  public Feedback(final @NotNull String message) {
    setMessage(message);
  }

  public Feedback(final @NotNull Feedback feedback) {
    this.message = feedback.message;
    this.contactEmail = feedback.contactEmail;
    this.name = feedback.name;
    this.associatedEventId = feedback.associatedEventId;
    this.replayId = feedback.replayId;
    this.url = feedback.url;
    this.unknown = CollectionUtils.newConcurrentHashMap(feedback.unknown);
  }

  public @Nullable String getContactEmail() {
    return contactEmail;
  }

  public void setContactEmail(final @Nullable String contactEmail) {
    this.contactEmail = contactEmail;
  }

  public @Nullable String getName() {
    return name;
  }

  public void setName(final @Nullable String name) {
    this.name = name;
  }

  public @Nullable SentryId getAssociatedEventId() {
    return associatedEventId;
  }

  public void setAssociatedEventId(final @NotNull SentryId associatedEventId) {
    this.associatedEventId = associatedEventId;
  }

  public @Nullable SentryId getReplayId() {
    return replayId;
  }

  public void setReplayId(final @NotNull SentryId replayId) {
    this.replayId = replayId;
  }

  public @Nullable String getUrl() {
    return url;
  }

  public void setUrl(final @Nullable String url) {
    this.url = url;
  }

  public @NotNull String getMessage() {
    return message;
  }

  public void setMessage(final @NotNull String message) {
    // Sentry limits the message to 4096 characters
    if (message.length() > 4096) {
      this.message = message.substring(0, 4096);
    } else {
      this.message = message;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Feedback)) return false;
    Feedback feedback = (Feedback) o;
    return Objects.equals(message, feedback.message)
        && Objects.equals(contactEmail, feedback.contactEmail)
        && Objects.equals(name, feedback.name)
        && Objects.equals(associatedEventId, feedback.associatedEventId)
        && Objects.equals(replayId, feedback.replayId)
        && Objects.equals(url, feedback.url)
        && Objects.equals(unknown, feedback.unknown);
  }

  @Override
  public String toString() {
    return "Feedback{"
        + "message='"
        + message
        + '\''
        + ", contactEmail='"
        + contactEmail
        + '\''
        + ", name='"
        + name
        + '\''
        + ", associatedEventId="
        + associatedEventId
        + ", replayId="
        + replayId
        + ", url='"
        + url
        + '\''
        + ", unknown="
        + unknown
        + '}';
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, contactEmail, name, associatedEventId, replayId, url, unknown);
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

      Feedback feedback = new Feedback(message);
      feedback.contactEmail = contactEmail;
      feedback.name = name;
      feedback.associatedEventId = associatedEventId;
      feedback.replayId = replayId;
      feedback.url = url;
      feedback.unknown = unknown;
      return feedback;
    }
  }
}
