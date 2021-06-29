package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.JsonReaderUtils;
import io.sentry.vendor.gson.stream.JsonReader;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adds additional information about what happened to an event. */
public final class UserFeedback implements JsonSerializable {

  private final SentryId eventId;
  private @Nullable String name;
  private @Nullable String email;
  private @Nullable String comments;

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

  // JsonSerializable

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.EVENT_ID);
    writer.value(eventId.toString());
    if (name != null) {
      writer.name(JsonKeys.NAME);
      writer.value(name);
    }
    if (email != null) {
      writer.name(JsonKeys.EMAIL);
      writer.value(email);
    }
    if (comments != null) {
      writer.name(JsonKeys.COMMENTS);
      writer.value(comments);
    }
    writer.endObject();
  }

  // JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<UserFeedback> {
    @Override
    public @NotNull UserFeedback deserialize(@NotNull JsonReader reader, @NotNull ILogger logger)
        throws Exception {
      SentryId sentryId = null;
      String name = null;
      String email = null;
      String comments = null;

      reader.beginObject();
      do {
        String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.EVENT_ID:
            sentryId = new SentryId(reader.nextString());
            break;
          case JsonKeys.NAME:
            name = JsonReaderUtils.nextStringOrNull(reader);
            break;
          case JsonKeys.EMAIL:
            email = JsonReaderUtils.nextStringOrNull(reader);
            break;
          case JsonKeys.COMMENTS:
            comments = JsonReaderUtils.nextStringOrNull(reader);
            break;
          default:
            break;
        }
      } while (reader.hasNext());
      reader.endObject();

      if (sentryId == null) {
        String message = "Missing required field \"" + JsonKeys.EVENT_ID + "\"";
        Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      return new UserFeedback(sentryId, name, email, comments);
    }
  }
}
