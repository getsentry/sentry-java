package io.sentry.util.network;

import java.util.List;
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
  }
}
