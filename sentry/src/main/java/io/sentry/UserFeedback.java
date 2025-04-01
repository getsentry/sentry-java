package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adds additional information about what happened to an event. */
public final class UserFeedback implements JsonUnknown, JsonSerializable {

  private final SentryId eventId;
  private @Nullable String name;
  private @Nullable String email;
  private @Nullable String comments;

  private @Nullable Map<String, Object> unknown;

  /**
   * Initializes SentryUserFeedback and sets the required eventId.
   *
   * @param eventId The eventId of the event to which the user feedback is associated.
   */
  public UserFeedback(SentryId eventId) {
    this(eventId, null, null, null);
  }

  /**
   * Initializes SentryUserFeedback and sets the required eventId.
   *
   * @param eventId The eventId of the event to which the user feedback is associated.
   * @param name the name of the user.
   * @param email the email of the user.
   * @param comments comments of the user about what happened.
   */
  public UserFeedback(
      SentryId eventId, @Nullable String name, @Nullable String email, @Nullable String comments) {
    this.eventId = eventId;
    this.name = name;
    this.email = email;
    this.comments = comments;
  }

  /**
   * Gets the eventId of the event to which the user feedback is associated.
   *
   * @return the eventId
   */
  public SentryId getEventId() {
    return eventId;
  }

  /**
   * Gets the name of the user.
   *
   * @return the name.
   */
  public @Nullable String getName() {
    return name;
  }

  /**
   * Sets the name of the user.
   *
   * @param name the name of the user.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the email of the user.
   *
   * @return the email.
   */
  public @Nullable String getEmail() {
    return email;
  }

  /**
   * Sets the email of the user.
   *
   * @param email the email of the user.
   */
  public void setEmail(@Nullable String email) {
    this.email = email;
  }

  /**
   * Gets comments of the user about what happened.
   *
   * @return the comments
   */
  public @Nullable String getComments() {
    return comments;
  }

  /**
   * Sets comments of the user about what happened.
   *
   * @param comments the comments
   */
  public void setComments(@Nullable String comments) {
    this.comments = comments;
  }

  @Override
  public String toString() {
    return "UserFeedback{"
        + "eventId="
        + eventId
        + ", name='"
        + name
        + '\''
        + ", email='"
        + email
        + '\''
        + ", comments='"
        + comments
        + '\''
        + '}';
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String EVENT_ID = "event_id";
    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String COMMENTS = "comments";
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
    writer.name(JsonKeys.EVENT_ID);
    eventId.serialize(writer, logger);
    if (name != null) {
      writer.name(JsonKeys.NAME).value(name);
    }
    if (email != null) {
      writer.name(JsonKeys.EMAIL).value(email);
    }
    if (comments != null) {
      writer.name(JsonKeys.COMMENTS).value(comments);
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

  public static final class Deserializer implements JsonDeserializer<UserFeedback> {
    @Override
    public @NotNull UserFeedback deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      SentryId sentryId = null;
      String name = null;
      String email = null;
      String comments = null;
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.EVENT_ID:
            sentryId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case JsonKeys.NAME:
            name = reader.nextStringOrNull();
            break;
          case JsonKeys.EMAIL:
            email = reader.nextStringOrNull();
            break;
          case JsonKeys.COMMENTS:
            comments = reader.nextStringOrNull();
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

      if (sentryId == null) {
        String message = "Missing required field \"" + JsonKeys.EVENT_ID + "\"";
        Exception exception = new IllegalStateException(message);
        if (logger.isEnabled(SentryLevel.ERROR)) {
          logger.log(SentryLevel.ERROR, message, exception);
        }
        throw exception;
      }

      UserFeedback userFeedback = new UserFeedback(sentryId, name, email, comments);
      userFeedback.setUnknown(unknown);
      return userFeedback;
    }
  }
}
