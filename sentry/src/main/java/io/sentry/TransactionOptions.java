package io.sentry;

import org.jetbrains.annotations.Nullable;

/** Sentry Transaction options */
public final class TransactionOptions extends SpanOptions {

  /**
   * Arbitrary data used in {@link SamplingContext} to determine if transaction is going to be
   * sampled.
   */
  private @Nullable CustomSamplingContext customSamplingContext = null;

  /** Defines if transaction should be bound to scope */
  private boolean bindToScope = false;

  /** The start timestamp of the transaction */
  private @Nullable SentryDate startTimestamp = null;

  /**
   * When `waitForChildren` is set to `true`, tracer will finish only when both conditions are met
   * (the order of meeting condition does not matter): - tracer itself is finished - all child spans
   * are finished.
   */
  private boolean waitForChildren = false;

  /**
   * The idle time, measured in ms, to wait until the transaction will be finished. The span will
   * use the end timestamp of the last finished span as the endtime for the transaction.
   *
   * <p>When set to {@code null} the transaction must be finished manually.
   *
   * <p>The default is 3 seconds.
   */
  private @Nullable Long idleTimeout = null;

  /**
   * When `waitForChildren` is set to `true` and this callback is set, it's called before the
   * transaction is captured.
   */
  private @Nullable TransactionFinishedCallback transactionFinishedCallback = null;

  /**
   * Gets the customSamplingContext
   *
   * @return customSamplingContext - the customSamplingContext
   */
  public @Nullable CustomSamplingContext getCustomSamplingContext() {
    return customSamplingContext;
  }

  /**
   * Sets the customSamplingContext
   *
   * @param customSamplingContext - the customSamplingContext
   */
  public void setCustomSamplingContext(@Nullable CustomSamplingContext customSamplingContext) {
    this.customSamplingContext = customSamplingContext;
  }

  /**
   * Checks if bindToScope is enabled
   *
   * @return true if enabled or false otherwise
   */
  public boolean isBindToScope() {
    return bindToScope;
  }

  /**
   * Sets bindToScope to enabled or disabled
   *
   * @param bindToScope true if enabled or false otherwise
   */
  public void setBindToScope(boolean bindToScope) {
    this.bindToScope = bindToScope;
  }

  /**
   * Gets the startTimestamp
   *
   * @return startTimestamp - the startTimestamp
   */
  public @Nullable SentryDate getStartTimestamp() {
    return startTimestamp;
  }

  /**
   * Sets the startTimestamp
   *
   * @param startTimestamp - the startTimestamp
   */
  public void setStartTimestamp(@Nullable SentryDate startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  /**
   * Checks if waitForChildren is enabled
   *
   * @return true if enabled or false otherwise
   */
  public boolean isWaitForChildren() {
    return waitForChildren;
  }

  /**
   * Sets waitForChildren to enabled or disabled
   *
   * @param waitForChildren true if enabled or false otherwise
   */
  public void setWaitForChildren(boolean waitForChildren) {
    this.waitForChildren = waitForChildren;
  }

  /**
   * Gets the idleTimeout
   *
   * @return idleTimeout - the idleTimeout
   */
  public @Nullable Long getIdleTimeout() {
    return idleTimeout;
  }

  /**
   * Sets the idleTimeout
   *
   * @param idleTimeout - the idleTimeout
   */
  public void setIdleTimeout(@Nullable Long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  /**
   * Gets the transactionFinishedCallback callback
   *
   * @return transactionFinishedCallback - the transactionFinishedCallback callback
   */
  public @Nullable TransactionFinishedCallback getTransactionFinishedCallback() {
    return transactionFinishedCallback;
  }

  /**
   * Sets the transactionFinishedCallback callback
   *
   * @param transactionFinishedCallback - the transactionFinishedCallback callback
   */
  public void setTransactionFinishedCallback(
      @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    this.transactionFinishedCallback = transactionFinishedCallback;
  }
}
