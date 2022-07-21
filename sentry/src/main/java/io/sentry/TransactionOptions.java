package io.sentry;

import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TransactionOptions {

  private @Nullable CustomSamplingContext customSamplingContext = null;
  private boolean isBindToScope = false;
  private @Nullable Date startTimestamp = null;
  private boolean isWaitForChildren = false;
  private @Nullable Long idleTimeout = null;
  private boolean isTrimEnd = false;
  private @Nullable TransactionFinishedCallback transactionFinishedCallback = null;

  public @Nullable CustomSamplingContext getCustomSamplingContext() {
    return customSamplingContext;
  }

  public void setCustomSamplingContext(CustomSamplingContext customSamplingContext) {
    this.customSamplingContext = customSamplingContext;
  }

  public boolean isBindToScope() {
    return isBindToScope;
  }

  public void setBindToScope(boolean bindToScope) {
    isBindToScope = bindToScope;
  }

  public @Nullable Date getStartTimestamp() {
    return startTimestamp;
  }

  public void setStartTimestamp(@Nullable Date startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  public boolean isWaitForChildren() {
    return isWaitForChildren;
  }

  public void setWaitForChildren(boolean waitForChildren) {
    isWaitForChildren = waitForChildren;
  }

  public @Nullable Long getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(@Nullable Long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public boolean isTrimEnd() {
    return isTrimEnd;
  }

  public void setTrimEnd(boolean trimEnd) {
    isTrimEnd = trimEnd;
  }

  public @Nullable TransactionFinishedCallback getTransactionFinishedCallback() {
    return transactionFinishedCallback;
  }

  public void setTransactionFinishedCallback(
      @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    this.transactionFinishedCallback = transactionFinishedCallback;
  }
}
