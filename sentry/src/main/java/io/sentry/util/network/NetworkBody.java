package io.sentry.util.network;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the body content of a network request or response. Can be one of: JSON object, JSON
 * array, or string.
 *
 * <p>See <a
 * href="https://github.com/getsentry/sentry-javascript/blob/develop/packages/replay-internal/src/types/request.ts">Javascript
 * types</a>
 */
@ApiStatus.Internal
public final class NetworkBody {

  private final @Nullable Object body;
  private final @Nullable List<NetworkBodyWarning> warnings;

  public NetworkBody(final @Nullable Object body) {
    this(body, null);
  }

  public NetworkBody(
      final @Nullable Object body, final @Nullable List<NetworkBodyWarning> warnings) {
    this.body = body;
    this.warnings = warnings;
  }

  public @Nullable Object getBody() {
    return body;
  }

  public @Nullable List<NetworkBodyWarning> getWarnings() {
    return warnings;
  }

  public enum NetworkBodyWarning {
    JSON_TRUNCATED("JSON_TRUNCATED"),
    TEXT_TRUNCATED("TEXT_TRUNCATED"),
    INVALID_JSON("INVALID_JSON"),
    BODY_PARSE_ERROR("BODY_PARSE_ERROR");

    private final String value;

    NetworkBodyWarning(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  @Override
  public String toString() {
    return "NetworkBody{" + "body=" + body + ", warnings=" + warnings + '}';
  }
}
