package io.sentry.util.network;

import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data class for tracking HTTP request or response information in replays.
 * Captures essential information about the request/response without consuming
 * the actual body stream.
 */
public final class ReplayNetworkRequestOrResponse {
  private final @Nullable Long size;
  private final @Nullable Object body; // Can be Map<*, *>, List<*>, or String
  private final @NotNull Map<String, String> headers;

  public ReplayNetworkRequestOrResponse(
      @Nullable Long size,
      @Nullable Object body,
      @NotNull Map<String, String> headers) {
    this.size = size;
    this.body = body;
    this.headers = headers != null ? headers : Collections.emptyMap();
  }

  public ReplayNetworkRequestOrResponse() {
    this(null, null, Collections.emptyMap());
  }

  public @Nullable Long getSize() {
    return size;
  }

  public @Nullable Object getBody() {
    return body;
  }

  public @NotNull Map<String, String> getHeaders() {
    return headers;
  }

  @Override
  public String toString() {
    return "ReplayNetworkRequestOrResponse{" +
        "size=" + size +
        ", body=" + body +
        ", headers=" + headers +
        '}';
  }
}