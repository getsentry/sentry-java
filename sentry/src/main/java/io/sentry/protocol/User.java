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

  private @Nullable String segment;

  /** Remote IP address of the user. */
  private @Nullable String ipAddress;

  /**
   * Additional arbitrary fields, as stored in the database (and sometimes as sent by clients). All
   * data from `self.other` should end up here after store normalization.
   */
  private @Nullable Map<String, @NotNull String> data;

  /** unknown fields, only internal usage. */
  private @Nullable Map<String, @NotNull Object> unknown;

  public User() {}

  public User(final @NotNull User user) {
    this.email = user.email;
    this.username = user.username;
    this.id = user.id;
    this.ipAddress = user.ipAddress;
    this.segment = user.segment;
    this.data = CollectionUtils.newConcurrentHashMap(user.data);
    this.unknown = CollectionUtils.newConcurrentHashMap(user.unknown);
  }

  /**
   * Creates user from a map.
   *
   * @param map - The user data as map
   * @return the user
   */
  @SuppressWarnings("unchecked")
  public static User fromMap(@NotNull Map<String, Object> map) {
    Map<String, Object> unknown = null;
    User user = new User();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      switch (entry.getKey()) {
        case JsonKeys.EMAIL:
          user.email = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.ID:
          user.id = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.USERNAME:
          user.username = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.SEGMENT:
          user.segment = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.IP_ADDRESS:
          user.ipAddress = (value instanceof String) ? (String) value : null;
          break;
        case JsonKeys.DATA:
          Map<Object, Object> data = (value instanceof Map) ? (Map<Object, Object>) value : null;
          if (data != null) {
            ConcurrentHashMap<String, String> userData = new ConcurrentHashMap<>();
            for (Map.Entry<Object, Object> dataEntry : data.entrySet()) {
              if ((dataEntry.getKey() instanceof String) && (dataEntry.getValue() instanceof String)) {
                userData.put((String) dataEntry.getKey(), (String) dataEntry.getValue());
              }
            }
            user.data = userData;
          }
          break;
        case JsonKeys.OTHER:
          Map<Object, Object> other = (value instanceof Map) ? (Map<Object, Object>) value : null;
          // restore `other` from legacy JSON
          if (other != null && (user.data == null || user.data.isEmpty())) {
            ConcurrentHashMap<String, String> userData = new ConcurrentHashMap<>();
            for (Map.Entry<Object, Object> otherEntry : other.entrySet()) {
              if ((otherEntry.getKey() instanceof String) && (otherEntry.getValue() instanceof String)) {
                userData.put((String) otherEntry.getKey(), (String) otherEntry.getValue());
              }
            }
            user.data = userData;
          }
          break;
        default:
          if (unknown == null) {
            unknown = new ConcurrentHashMap<>();
          }
          unknown.put(entry.getKey(), entry.getValue());
          break;
      }
    }
    user.unknown = unknown;
    return user;
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
   * Gets the segment of the user.
   *
   * @return the user segment.
   */
  public @Nullable String getSegment() {
    return segment;
  }

  /**
   * Sets the segment of the user.
   *
   * @param segment the segment.
   */
  public void setSegment(final @Nullable String segment) {
    this.segment = segment;
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
   * @deprecated use {{@link User#getData()}} instead
   * @return the other user data.
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public @Nullable Map<String, @NotNull String> getOthers() {
    return getData();
  }

  /**
   * Sets other user related data.
   *
   * @deprecated use {{@link User#setData(Map)}} instead
   * @param other the other user related data..
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public void setOthers(final @Nullable Map<String, @NotNull String> other) {
    this.setData(other);
  }

  /**
   * Gets additional arbitrary fields of the user.
   *
   * @return the other user data.
   */
  public @Nullable Map<String, @NotNull String> getData() {
    return data;
  }

  /**
   * Sets additional arbitrary fields of the user.
   *
   * @param data the other user related data..
   */
  public void setData(final @Nullable Map<String, @NotNull String> data) {
    this.data = CollectionUtils.newConcurrentHashMap(data);
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
    public static final String SEGMENT = "segment";
    public static final String IP_ADDRESS = "ip_address";
    public static final String OTHER = "other";
    public static final String DATA = "data";
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
    if (segment != null) {
      writer.name(JsonKeys.SEGMENT).value(segment);
    }
    if (ipAddress != null) {
      writer.name(JsonKeys.IP_ADDRESS).value(ipAddress);
    }
    if (data != null) {
      writer.name(JsonKeys.DATA).value(logger, data);
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
          case JsonKeys.SEGMENT:
            user.segment = reader.nextStringOrNull();
            break;
          case JsonKeys.IP_ADDRESS:
            user.ipAddress = reader.nextStringOrNull();
            break;
          case JsonKeys.DATA:
            user.data =
                CollectionUtils.newConcurrentHashMap(
                    (Map<String, String>) reader.nextObjectOrNull());
            break;
          case JsonKeys.OTHER:
            // restore `other` from legacy JSON
            if (user.data == null || user.data.isEmpty()) {
              user.data =
                  CollectionUtils.newConcurrentHashMap(
                      (Map<String, String>) reader.nextObjectOrNull());
            }
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
