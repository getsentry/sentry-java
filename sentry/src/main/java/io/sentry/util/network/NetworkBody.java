package io.sentry.util.network;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the body content of a network request or response. Can be one of: JSON object, JSON
 * array, or string.
 *
 * <p>See
 * https://github.com/getsentry/sentry-javascript/blob/develop/packages/replay-internal/src/types/request.ts
 */
public interface NetworkBody {

  /**
   * Creates a NetworkBody from a JSON object.
   *
   * @param value The map representing the JSON object
   * @return A NetworkBody instance for the JSON object
   */
  static @NotNull NetworkBody fromJsonObject(@NotNull Map<String, Object> value) {
    return new JsonObjectImpl(value);
  }

  /**
   * Creates a NetworkBody from a JSON array.
   *
   * @param value The list representing the JSON array
   * @return A NetworkBody instance for the JSON array
   */
  static @NotNull NetworkBody fromJsonArray(@NotNull List<Object> value) {
    return new JsonArrayImpl(value);
  }

  /**
   * Creates a NetworkBody from string content.
   *
   * @param value The string content
   * @return A NetworkBody instance for the string
   */
  static @NotNull NetworkBody fromString(@NotNull String value) {
    return new StringBodyImpl(value);
  }

  /**
   * Gets the underlying value of this NetworkBody.
   *
   * @return The value as an Object (could be Map, List, or String)
   */
  @NotNull
  Object getValue();

  /**
   * Checks if this NetworkBody represents a JSON object.
   *
   * @return true if this is a JSON object
   */
  default boolean isJsonObject() {
    return getValue() instanceof Map;
  }

  /**
   * Checks if this NetworkBody represents a JSON array.
   *
   * @return true if this is a JSON array
   */
  default boolean isJsonArray() {
    return getValue() instanceof List;
  }

  /**
   * Checks if this NetworkBody represents string content.
   *
   * @return true if this is a string
   */
  default boolean isString() {
    return getValue() instanceof String;
  }

  /**
   * Gets the value as a JSON object (Map).
   *
   * @return The Map if this is a JSON object, null otherwise
   */
  default @Nullable Map<String, Object> asJsonObject() {
    Object value = getValue();
    if (value instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) value;
      return map;
    }
    return null;
  }

  /**
   * Gets the value as a JSON array (List).
   *
   * @return The List if this is a JSON array, null otherwise
   */
  default @Nullable List<Object> asJsonArray() {
    Object value = getValue();
    if (value instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) value;
      return list;
    }
    return null;
  }

  /**
   * Gets the value as a string.
   *
   * @return The String if this is string content, null otherwise
   */
  default @Nullable String asString() {
    Object value = getValue();
    return value instanceof String ? (String) value : null;
  }

  // Private implementation classes

  /** Implementation for JSON object bodies */
  final class JsonObjectImpl implements NetworkBody {
    private final @NotNull Map<String, Object> value;

    JsonObjectImpl(@NotNull Map<String, Object> value) {
      this.value = value;
    }

    @Override
    public @NotNull Map<String, Object> getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "NetworkBody.JsonObject{" + value + '}';
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof JsonObjectImpl)) return false;
      JsonObjectImpl that = (JsonObjectImpl) obj;
      return value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  /** Implementation for JSON array bodies */
  final class JsonArrayImpl implements NetworkBody {
    private final @NotNull List<Object> value;

    JsonArrayImpl(@NotNull List<Object> value) {
      this.value = value;
    }

    @Override
    public @NotNull List<Object> getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "NetworkBody.JsonArray{" + value + '}';
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof JsonArrayImpl)) return false;
      JsonArrayImpl that = (JsonArrayImpl) obj;
      return value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  /** Implementation for string bodies */
  final class StringBodyImpl implements NetworkBody {
    private final @NotNull String value;

    StringBodyImpl(@NotNull String value) {
      this.value = value;
    }

    @Override
    public @NotNull String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "NetworkBody.StringBody{" + value + '}';
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof StringBodyImpl)) return false;
      StringBodyImpl that = (StringBodyImpl) obj;
      return value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }
}
