package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry Transaction options */
public final class TransactionOptions extends SpanOptions {

  @ApiStatus.Internal public static final long DEFAULT_DEADLINE_TIMEOUT_AUTO_TRANSACTION = 30000;

  /**
   * Arbitrary data used in {@link SamplingContext} to determine if transaction is going to be
   * sampled.
   */
  private @Nullable CustomSamplingContext customSamplingContext = null;

  /** Defines if transaction should be bound to scope */
  private boolean bindToScope = false;

  /** Defines if transaction refers to the app start process */
  private boolean isAppStartTransaction = false;

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
   * The deadline time, measured in ms, to wait until the transaction will be force-finished with
   * deadline-exceeded status./
   *
   * <p>When set to {@code null} the transaction won't be forcefully finished.
   *
   * <p>The default is 30 seconds.
   */
  private @Nullable Long deadlineTimeout = null;

  /**
   * When `waitForChildren` is set to `true` and this callback is set, it's called before the
   * transaction is captured.
   */
  private @Nullable TransactionFinishedCallback transactionFinishedCallback = null;

  /** Span factory to use. Uses factory configured in {@link SentryOptions} if `null`. */
  @ApiStatus.Internal private @Nullable ISpanFactory spanFactory = null;

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
   * Sets the deadlineTimeout. If set, an transaction and it's child spans will be force-finished
   * with status {@link SpanStatus#DEADLINE_EXCEEDED} in case the transaction isn't finished in
   * time.
   *
   * @param deadlineTimeoutMs - the deadlineTimeout, in ms - or null if no deadline should be set
   */
  @ApiStatus.Internal
  public void setDeadlineTimeout(@Nullable Long deadlineTimeoutMs) {
    this.deadlineTimeout = deadlineTimeoutMs;
  }

  /**
   * Gets the deadlineTimeout
   *
   * @return deadlineTimeout - the deadlineTimeout, in ms - or null if no deadline is set
   */
  @ApiStatus.Internal
  public @Nullable Long getDeadlineTimeout() {
    return deadlineTimeout;
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

  @ApiStatus.Internal
  public void setAppStartTransaction(final boolean appStartTransaction) {
    isAppStartTransaction = appStartTransaction;
  }

  @ApiStatus.Internal
  public boolean isAppStartTransaction() {
    return isAppStartTransaction;
  }

  @ApiStatus.Internal
  public @Nullable ISpanFactory getSpanFactory() {
    return this.spanFactory;
  }

  @ApiStatus.Internal
  public void setSpanFactory(final @NotNull ISpanFactory spanFactory) {
    this.spanFactory = spanFactory;
  }
}
