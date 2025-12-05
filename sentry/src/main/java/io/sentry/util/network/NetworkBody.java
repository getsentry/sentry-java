package io.sentry.util.network;

import io.sentry.JsonRawString;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the body content of a network request or response. Can be one of: JSON object, JSON
 * array, or string.
 *
 * <p>See <a
 * href="https://github.com/getsentry/sentry-javascript/blob/develop/packages/replay-internal/src/types/request.ts">Javascript
 * types</a>
 */
public interface NetworkBody {

  /**
   * Creates a NetworkBody from a Java object which should be serialized to JSON.
   *
   * @param value The map representing the JSON object
   * @return A NetworkBody instance for the JSON object
   */
  static @NotNull NetworkBody fromJsonObject(@NotNull final Map<String, Object> value) {
    return new JsonObjectImpl(value);
  }

  /**
   * Creates a NetworkBody from string content.
   *
   * @param value The string content
   * @return A NetworkBody instance for the string
   */
  static @NotNull NetworkBody fromString(@NotNull final String value) {
    return new StringBodyImpl(value);
  }

  static @NotNull NetworkBody fromRawJson(@NotNull String rawJson) {
    return new JsonRawImpl(rawJson);
  }

  /**
   * Gets the underlying value of this NetworkBody.
   *
   * @return The value as an Object (could be Map, List, or String)
   */
  @NotNull
  Object getValue();

  // Private implementation classes

  /** Implementation for JSON object bodies */
  final class JsonObjectImpl implements NetworkBody {
    private final @NotNull Map<String, Object> value;

    JsonObjectImpl(@NotNull final Map<String, Object> value) {
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
  }

  final class JsonRawImpl implements NetworkBody {
    private final @NotNull JsonRawString value;

    JsonRawImpl(@NotNull final String value) {
      this.value = new JsonRawString(value);
    }

    @Override
    public @NotNull JsonRawString getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "JsonRawImpl{" + value + '}';
    }
  }

  /** Implementation for string bodies */
  final class StringBodyImpl implements NetworkBody {
    private final @NotNull String value;

    StringBodyImpl(@NotNull final String value) {
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
  }
}
