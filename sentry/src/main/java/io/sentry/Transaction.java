package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public final class Transaction extends SentryBaseEvent<TransactionContexts> {
  /**
   * The transaction name.
   */
  private @Nullable String transaction;
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String platform = "java";
  private @NotNull Date startTimestamp;
  /**
   * End timestamp.
   */
  private @Nullable Date timestamp;

  /**
   * Creates unnamed transaction.
   */
  public Transaction() {
    this(null);
  }

  /**
   * Creates transaction with name.
   * @param name - transaction name
   */
  public Transaction(final @Nullable String name) {
    this.transaction = name;
  }

  /**
   * Creates transaction with name and contexts.
   *
   * @param name - transaction name
   * @param contexts - transaction contexts
   */
  public Transaction(final @Nullable String name, final @Nullable TransactionContexts contexts) {
    this.transaction = name;
    this.startTimestamp = DateUtils.getCurrentDateTime();
    if (contexts == null) {
      this.setContexts(new TransactionContexts());
    } else {
      this.setContexts(contexts);
    }
    if (this.getContexts().getTrace() == null) {
      this.getContexts().setTrace(new Trace());
    }
    this.getContexts().getTrace().setTraceId(new SentryId());
    this.getContexts().getTrace().setSpanId(new SpanId());
  }

  /**
   * Sets transaction name.
   * @param name - transaction name
   */
  public void setName(@Nullable String name) {
    this.transaction = name;
  }

  public void finish() {
    this.timestamp = DateUtils.getCurrentDateTime();
  }

  public String getTransaction() {
    return transaction;
  }

  public Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }
}
