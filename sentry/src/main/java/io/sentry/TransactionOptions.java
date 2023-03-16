package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TransactionOptions extends SpanOptions {

  private @Nullable CustomSamplingContext customSamplingContext = null;
  private boolean bindToScope = false;
  private @Nullable SentryDate startTimestamp = null;
  private boolean waitForChildren = false;

  private @Nullable Long idleTimeout = null;
  private @Nullable TransactionFinishedCallback transactionFinishedCallback = null;

  public @Nullable CustomSamplingContext getCustomSamplingContext() {
    return customSamplingContext;
  }

  public void setCustomSamplingContext(@Nullable CustomSamplingContext customSamplingContext) {
    this.customSamplingContext = customSamplingContext;
  }

  public boolean isBindToScope() {
    return bindToScope;
  }

  public void setBindToScope(boolean bindToScope) {
    this.bindToScope = bindToScope;
  }

  public @Nullable SentryDate getStartTimestamp() {
    return startTimestamp;
  }

  public void setStartTimestamp(@Nullable SentryDate startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  /**
   * When `waitForChildren` is set to `true`, tracer will finish only when both conditions are met
   * (the order of meeting condition does not matter): - tracer itself is finished - all child spans
   * are finished.
   */
  public boolean isWaitForChildren() {
    return waitForChildren;
  }

  public void setWaitForChildren(boolean waitForChildren) {
    this.waitForChildren = waitForChildren;
  }

  /**
   * The idle time, measured in ms, to wait until the transaction will be finished. The span will
   * use the end timestamp of the last finished span as the endtime for the transaction.
   *
   * <p>When set to {@code null} the transaction must be finished manually.
   *
   * <p>The default is 3 seconds.
   */
  public @Nullable Long getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(@Nullable Long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public @Nullable TransactionFinishedCallback getTransactionFinishedCallback() {
    return transactionFinishedCallback;
  }

  public void setTransactionFinishedCallback(
      @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    this.transactionFinishedCallback = transactionFinishedCallback;
  }
}
