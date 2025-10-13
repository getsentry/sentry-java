package io.sentry.util.network;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the body content of a network request or response. Can be one of: JsonObject,
 * JsonArray, or String.
 *
 * <p> See https://github.com/getsentry/sentry-javascript/blob/develop/packages/replay-internal/src/types/request.ts
 */
public interface NetworkBody {

  /** Represents a JSON object body (key-value pairs) */
  public static final class JsonObject implements NetworkBody {
    private final @NotNull Map<String, Object> value;

    public JsonObject(@NotNull Map<String, Object> value) {
      this.value = value;
    }

    public @NotNull Map<String, Object> getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "JsonObject{" + value + '}';
    }
  }

  /** Represents a JSON array body (ordered list) */
  public static final class JsonArray implements NetworkBody {
    private final @NotNull List<Object> value;

    public JsonArray(@NotNull List<Object> value) {
      this.value = value;
    }

    public @NotNull List<Object> getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "JsonArray{" + value + '}';
    }
  }

  /** Represents a string body (text content) */
  public static final class StringBody implements NetworkBody {
    private final @NotNull String value;

    public StringBody(@NotNull String value) {
      this.value = value;
    }

    public @NotNull String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "StringBody{" + value + '}';
    }
  }
}
