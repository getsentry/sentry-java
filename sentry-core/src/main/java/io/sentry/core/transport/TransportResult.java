package io.sentry.core.transport;

import io.sentry.core.SentryEvent;

/**
 * A result of {@link ITransport#send(SentryEvent)}. Note that this class is intentionally not
 * subclassable and has only two factory methods to capture the 2 possible states - success or
 * error.
 */
public abstract class TransportResult {

  /**
   * Use this method to announce success of sending the event.
   *
   * @return a successful transport result
   */
  public static TransportResult success() {
    return SuccessTransportResult.INSTANCE;
  }

  /**
   * Use this method to announce failure of sending the event.
   *
   * @param retryMillis the number of milliseconds after which the next attempt to send the event
   *     should be made or -1 if not known
   * @param responseCode the HTTP status code if known, -1 otherwise
   * @return an erroneous transport result
   */
  public static TransportResult error(long retryMillis, int responseCode) {
    return new ErrorTransportResult(retryMillis, responseCode);
  }

  private TransportResult() {}

  public abstract boolean isSuccess();

  public abstract long getRetryMillis();

  public abstract int getResponseCode();

  private static final class SuccessTransportResult extends TransportResult {
    static final SuccessTransportResult INSTANCE = new SuccessTransportResult();

    @Override
    public boolean isSuccess() {
      return true;
    }

    @Override
    public long getRetryMillis() {
      return -1;
    }

    @Override
    public int getResponseCode() {
      return -1;
    }
  }

  private static final class ErrorTransportResult extends TransportResult {
    private final long retryMillis;
    private final int responseCode;

    ErrorTransportResult(long retryMillis, int responseCode) {
      this.retryMillis = retryMillis;
      this.responseCode = responseCode;
    }

    @Override
    public boolean isSuccess() {
      return false;
    }

    @Override
    public long getRetryMillis() {
      return retryMillis;
    }

    @Override
    public int getResponseCode() {
      return responseCode;
    }
  }
}
