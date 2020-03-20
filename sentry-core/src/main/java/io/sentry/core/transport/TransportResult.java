package io.sentry.core.transport;

import io.sentry.core.SentryEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A result of {@link ITransport#send(SentryEvent)}. Note that this class is intentionally not
 * subclassable and has only two factory methods to capture the 2 possible states - success or
 * error.
 */
abstract class TransportResult {

  /**
   * Use this method to announce success of sending the event.
   *
   * @return a successful transport result
   */
  public static @NotNull TransportResult success() {
    return SuccessTransportResult.INSTANCE;
  }

  /**
   * Use this method to announce failure of sending the event.
   *
   * @param responseCode the HTTP status code if known, -1 otherwise
   * @return an erroneous transport result
   */
  public static @NotNull TransportResult error(final int responseCode) {
    return new ErrorTransportResult(responseCode);
  }

  private TransportResult() {}

  public abstract boolean isSuccess();

  public abstract int getResponseCode();

  private static final class SuccessTransportResult extends TransportResult {
    static final SuccessTransportResult INSTANCE = new SuccessTransportResult();

    @Override
    public boolean isSuccess() {
      return true;
    }

    @Override
    public int getResponseCode() {
      return -1;
    }
  }

  private static final class ErrorTransportResult extends TransportResult {
    private final int responseCode;

    ErrorTransportResult(final int responseCode) {
      this.responseCode = responseCode;
    }

    @Override
    public boolean isSuccess() {
      return false;
    }

    @Override
    public int getResponseCode() {
      return responseCode;
    }
  }
}
