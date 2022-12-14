package io.sentry;

import java.util.Date;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TransactionOptions {

  private @Nullable CustomSamplingContext customSamplingContext = null;
  private boolean bindToScope = false;
  private @Nullable Date startTimestamp = null;
  private boolean waitForChildren = false;
  private @Nullable Long idleTimeout = null;
  private boolean trimEnd = false;
  private @Nullable TransactionFinishedCallback transactionFinishedCallback = null;

  public @Nullable CustomSamplingContext getCustomSamplingContext() {
    return customSamplingContext;
  }

  public void setCustomSamplingContext(CustomSamplingContext customSamplingContext) {
    this.customSamplingContext = customSamplingContext;
  }

  public boolean isBindToScope() {
    return bindToScope;
  }

  public void setBindToScope(boolean bindToScope) {
    this.bindToScope = bindToScope;
  }

  public @Nullable Date getStartTimestamp() {
    return startTimestamp;
  }

  public void setStartTimestamp(@Nullable Date startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  public boolean isWaitForChildren() {
    return waitForChildren;
  }

  public void setWaitForChildren(boolean waitForChildren) {
    this.waitForChildren = waitForChildren;
  }

  public @Nullable Long getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(@Nullable Long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public boolean isTrimEnd() {
    return trimEnd;
  }

  public void setTrimEnd(boolean trimEnd) {
    this.trimEnd = trimEnd;
  }

  public @Nullable TransactionFinishedCallback getTransactionFinishedCallback() {
    return transactionFinishedCallback;
  }

  public void setTransactionFinishedCallback(
      @Nullable TransactionFinishedCallback transactionFinishedCallback) {
    this.transactionFinishedCallback = transactionFinishedCallback;
  }
}
