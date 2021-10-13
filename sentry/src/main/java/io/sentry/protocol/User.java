package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about the user who triggered an event.
 *
 * <p>```json { "user": { "id": "unique_id", "username": "my_user", "email": "foo@example.com",
 * "ip_address": "127.0.0.1", "subscription": "basic" } } ```
 */
public final class User implements JsonUnknown, JsonSerializable {

  /** Email address of the user. */
  private @Nullable String email;

  /** Unique identifier of the user. */
  private @Nullable String id;

  /** Username of the user. */
  private @Nullable String username;

  /** Remote IP address of the user. */
  private @Nullable String ipAddress;

  /**
   * Additional arbitrary fields, as stored in the database (and sometimes as sent by clients). All
   * data from `self.other` should end up here after store normalization.
   */
  private @Nullable Map<String, @NotNull String> other;

  /** unknown fields, only internal usage. */
  private @Nullable Map<String, @NotNull Object> unknown;

  public User() {}

  public User(final @NotNull User user) {
    this.email = user.email;
    this.username = user.username;
    this.id = user.id;
    this.ipAddress = user.ipAddress;
    this.other = CollectionUtils.newConcurrentHashMap(user.other);
    this.unknown = CollectionUtils.newConcurrentHashMap(user.unknown);
  }

  /**
   * Gets the e-mail address of the user.
   *
   * @return the e-mail.
   */
  public @Nullable String getEmail() {
    return email;
  }

  /**
   * Gets the e-mail address of the user.
   *
   * @param email the e-mail.
   */
  public void setEmail(final @Nullable String email) {
    this.email = email;
  }

  /**
   * Gets the id of the user.
   *
   * @return the id.
   */
  public @Nullable String getId() {
    return id;
  }

  /**
   * Sets the id of the user.
   *
   * @param id the user id.
   */
  public void setId(final @Nullable String id) {
    this.id = id;
  }

  /**
   * Gets the username of the user.
   *
   * @return the username.
   */
  public @Nullable String getUsername() {
    return username;
  }

  /**
   * Sets the username of the user.
   *
   * @param username the username.
   */
  public void setUsername(final @Nullable String username) {
    this.username = username;
  }

  /**
   * Gets the IP address of the user.
   *
   * @return the IP address of the user.
   */
  public @Nullable String getIpAddress() {
    return ipAddress;
  }

  /**
   * Sets the IP address of the user.
   *
   * @param ipAddress the IP address of the user.
   */
  public void setIpAddress(final @Nullable String ipAddress) {
    this.ipAddress = ipAddress;
  }

  /**
   * Gets other user related data.
   *
   * @return the other user data.
   */
  public @Nullable Map<String, @NotNull String> getOthers() {
    return other;
  }

  /**
   * Sets other user related data.
   *
   * @param other the other user related data..
   */
  public void setOthers(final @Nullable Map<String, @NotNull String> other) {
    this.other = CollectionUtils.newConcurrentHashMap(other);
  }

  // region json

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class JsonKeys {
    public static final String EMAIL = "email";
    public static final String ID = "id";
    public static final String USERNAME = "username";
    public static final String IP_ADDRESS = "ip_address";
    public static final String OTHER = "other";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (email != null) {
      writer.name(JsonKeys.EMAIL).value(email);
    }
    if (id != null) {
      writer.name(JsonKeys.ID).value(id);
    }
    if (username != null) {
      writer.name(JsonKeys.USERNAME).value(username);
    }
    if (ipAddress != null) {
      writer.name(JsonKeys.IP_ADDRESS).value(ipAddress);
    }
    if (other != null) {
      writer.name(JsonKeys.OTHER).value(logger, other);
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

  public static final class Deserializer implements JsonDeserializer<User> {
    @SuppressWarnings("unchecked")
    @Override
    public @NotNull User deserialize(@NotNull JsonObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      User user = new User();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.EMAIL:
            user.email = reader.nextStringOrNull();
            break;
          case JsonKeys.ID:
            user.id = reader.nextStringOrNull();
            break;
          case JsonKeys.USERNAME:
            user.username = reader.nextStringOrNull();
            break;
          case JsonKeys.IP_ADDRESS:
            user.ipAddress = reader.nextStringOrNull();
            break;
          case JsonKeys.OTHER:
            user.other =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, String>) reader.nextObjectOrNull());
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      user.setUnknown(unknown);
      reader.endObject();
      return user;
    }
  }

  // endregion
}
