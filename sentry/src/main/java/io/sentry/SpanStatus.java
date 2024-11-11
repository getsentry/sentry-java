package io.sentry;

import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum SpanStatus implements JsonSerializable {
  /** Not an error, returned on success. */
  OK(0, 399),
  /** The operation was cancelled, typically by the caller. */
  CANCELLED(499),
  /**
   * Some invariants expected by the underlying system have been broken. This code is reserved for
   * serious errors.
   */
  INTERNAL_ERROR(500),
  /** An unknown error raised by APIs that don't return enough error information. */
  UNKNOWN(500),
  /** An unknown error raised by APIs that don't return enough error information. */
  UNKNOWN_ERROR(500),
  /** The client specified an invalid argument. */
  INVALID_ARGUMENT(400),
  /** The deadline expired before the operation could succeed. */
  DEADLINE_EXCEEDED(504),

  /** Content was not found or request was denied for an entire class of users. */
  NOT_FOUND(404),

  /** The entity attempted to be created already exists */
  ALREADY_EXISTS(409),

  /** The caller doesn't have permission to execute the specified operation. */
  PERMISSION_DENIED(403),

  /** The resource has been exhausted e.g. per-user quota exhausted, file system out of space. */
  RESOURCE_EXHAUSTED(429),

  /** The client shouldn't retry until the system state has been explicitly handled. */
  FAILED_PRECONDITION(400),

  /** The operation was aborted. */
  ABORTED(409),

  /** The operation was attempted past the valid range e.g. seeking past the end of a file. */
  OUT_OF_RANGE(400),

  /** The operation is not implemented or is not supported/enabled for this operation. */
  UNIMPLEMENTED(501),

  /** The service is currently available e.g. as a transient condition. */
  UNAVAILABLE(503),

  /** Unrecoverable data loss or corruption. */
  DATA_LOSS(500),

  /** The requester doesn't have valid authentication credentials for the operation. */
  UNAUTHENTICATED(401);

  private final int minHttpStatusCode;
  private final int maxHttpStatusCode;

  SpanStatus(int httpStatusCode) {
    this.minHttpStatusCode = httpStatusCode;
    this.maxHttpStatusCode = httpStatusCode;
  }

  SpanStatus(int minHttpStatusCode, int maxHttpStatusCode) {
    this.minHttpStatusCode = minHttpStatusCode;
    this.maxHttpStatusCode = maxHttpStatusCode;
  }

  /**
   * Creates {@link SpanStatus} from HTTP status code.
   *
   * @param httpStatusCode the http status code
   * @return span status equivalent of http status code or null if not found
   */
  public static @Nullable SpanStatus fromHttpStatusCode(final int httpStatusCode) {
    for (final SpanStatus status : SpanStatus.values()) {
      if (status.matches(httpStatusCode)) {
        return status;
      }
    }
    return null;
  }

  /**
   * Creates {@link SpanStatus} from HTTP status code.
   *
   * @param httpStatusCode the http status code
   * @param defaultStatus the default SpanStatus
   * @return span status equivalent of http status code or defaultStatus if not found
   */
  public static @NotNull SpanStatus fromHttpStatusCode(
      final @Nullable Integer httpStatusCode, final @NotNull SpanStatus defaultStatus) {
    final SpanStatus spanStatus =
        httpStatusCode != null ? fromHttpStatusCode(httpStatusCode) : defaultStatus;
    return spanStatus != null ? spanStatus : defaultStatus;
  }

  private boolean matches(int httpStatusCode) {
    return httpStatusCode >= minHttpStatusCode && httpStatusCode <= maxHttpStatusCode;
  }

  public @NotNull String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static @Nullable SpanStatus fromApiNameSafely(final @Nullable String apiName) {
    if (apiName == null) {
      return null;
    }
    try {
      return SpanStatus.valueOf(apiName.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(apiName());
  }

  public static final class Deserializer implements JsonDeserializer<SpanStatus> {

    @Override
    public @NotNull SpanStatus deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return SpanStatus.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
