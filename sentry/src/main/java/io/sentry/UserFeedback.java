package io.sentry;

import io.sentry.json.JsonDeserializable;
import io.sentry.json.JsonSerializable;
import io.sentry.json.stream.JsonReader;
import io.sentry.json.stream.JsonToken;
import io.sentry.json.stream.JsonWriter;
import io.sentry.protocol.SentryId;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;

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

  // JsonSerializable

  @Override
  public void toJson(@NotNull JsonWriter jsonWriter) throws IOException {
    jsonWriter.beginObject();
    jsonWriter.name("event_id");
    jsonWriter.value(eventId.toString());
    if (name != null) {
      jsonWriter.name("name");
      jsonWriter.value(name);
    }
    if (email != null) {
      jsonWriter.name("email");
      jsonWriter.value(email);
    }
    if (comments != null) {
      jsonWriter.name("comments");
      jsonWriter.value(comments);
    }
    jsonWriter.endObject();
  }

  // JsonDeserializable

  public static JsonDeserializable<UserFeedback> deserializer = json -> {
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.beginObject();

    SentryId sentryId = null;
    String name = null;
    String email = null;
    String comments = null;

    do {
      String nextName = reader.nextName();
      switch (nextName) {
        case "event_id":
          sentryId = new SentryId(reader.nextString());
          break;
        case "name":
          if (reader.peek() == JsonToken.STRING) {
            name = reader.nextString();
          } else {
            name = null;
          }
          break;
        case "email":
          if (reader.peek() == JsonToken.STRING) {
            email = reader.nextString();
          } else {
            email = null;
          }
          break;
        case "comments":
          if (reader.peek() == JsonToken.STRING) {
            comments = reader.nextString();
          } else {
            comments = null;
          }
          break;
        default:
          break;
      }
    } while (reader.hasNext());

    reader.endObject();

    if (sentryId == null) {
      throw new IllegalStateException("Missing required field \"sentryId\"");
    }

    return new UserFeedback(
      sentryId,
      name,
      email,
      comments
    );
  };
}
