package io.sentry.util.network;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data class for tracking HTTP request or response information in replays. See <a
 * href="https://github.com/getsentry/sentry-javascript/blob/develop/packages/replay-internal/src/types/request.ts">JS
 * SDK types</a>
 */
public final class ReplayNetworkRequestOrResponse {
  private final @Nullable Long size;
  private final @Nullable NetworkBody body;
  private final @NotNull Map<String, String> headers;

  public ReplayNetworkRequestOrResponse(
      @Nullable final Long size, @Nullable final NetworkBody body, @NotNull final Map<String, String> headers) {
    this.size = size;
    this.body = body;
    this.headers = headers;
  }

  public @Nullable Long getSize() {
    return size;
  }

  public @Nullable NetworkBody getBody() {
    return body;
  }

  public @NotNull Map<String, String> getHeaders() {
    return headers;
  }

  @Override
  public String toString() {
    return "ReplayNetworkRequestOrResponse{"
        + "size="
        + size
        + ", body="
        + body
        + ", headers="
        + headers
        + '}';
  }
}
